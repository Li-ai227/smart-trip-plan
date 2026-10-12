package com.smart.trip.agent.route.mcp;

import com.smart.trip.skills.observability.AgentMetrics;
import java.util.concurrent.Callable;

/**
 * 泛型 failover 引擎（C-007 增量 B / AC-9）。
 *
 * <p>消除 {@code FailoverGeocoder}/{@code FailoverRouter}/{@code FailoverTraffic} 之间
 * 的 failover + 熔断逻辑重复（CPD 门禁）。</p>
 */
final class FailoverExecutor {

    private static final System.Logger LOG = System.getLogger(FailoverExecutor.class.getName());

    private final VendorHealth primaryHealth;
    private final VendorHealth secondaryHealth;
    private final AgentMetrics metrics;

    FailoverExecutor(VendorHealth primaryHealth, VendorHealth secondaryHealth,
                     AgentMetrics metrics) {
        this.primaryHealth = primaryHealth;
        this.secondaryHealth = secondaryHealth;
        this.metrics = metrics;
    }

    /**
     * 按 failover 策略执行：先主厂商（如健康），失败后切备厂商。
     *
     * @param primaryCall   主厂商调用
     * @param secondaryCall 备厂商调用
     * @param <T>           返回值类型
     * @return 调用结果（真实数据，非离线降级）
     * @throws Exception 双厂商均失败
     */
    <T> T execute(Callable<T> primaryCall, Callable<T> secondaryCall) throws Exception {
        if (primaryHealth.isAvailable()) {
            try {
                T result = primaryCall.call();
                primaryHealth.recordSuccess();
                return result;
            } catch (Exception ex) {
                primaryHealth.recordFailure();
                logSwitch(primaryHealth.vendor(), secondaryHealth.vendor());
            }
        }

        if (secondaryHealth.isAvailable()) {
            try {
                T result = secondaryCall.call();
                secondaryHealth.recordSuccess();
                if (metrics != null) {
                    metrics.recordVendorSwitch(
                            primaryHealth.vendor().label(),
                            secondaryHealth.vendor().label());
                }
                return result;
            } catch (Exception ex) {
                secondaryHealth.recordFailure();
                throw ex;
            }
        }

        throw new MapApiException(-1,
                "双厂商均不可用: " + primaryHealth.vendor().label()
                        + "=" + primaryHealth.state()
                        + ", " + secondaryHealth.vendor().label()
                        + "=" + secondaryHealth.state());
    }

    private void logSwitch(VendorId from, VendorId to) {
        LOG.log(System.Logger.Level.WARNING,
                "Map vendor switch: {0} -> {1}", from.label(), to.label());
    }
}

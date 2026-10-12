package com.smart.trip.skills.governance;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import java.util.List;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 治理中间件（C-005 增量 B AC-9 / 决策 D-9）：把已验证的 {@link GovernedExternalCaller}
 * 治理链（限频→重试 1/2/4s→降级）包装为框架 {@link MiddlewareBase#onModelCall} 钩子。
 *
 * <p>{@code HarnessAgent.builder().middleware(new GovernanceMiddleware(...))} 构造的 Agent
 * 自动获得 C-005 AC-4 全部治理保证，无需显式传入 {@link GovernedExternalCaller}。
 * 内部委托 {@link GovernedExternalCaller#call} 而非复制治理逻辑（编码规范 §10 / PMD CPD）。</p>
 *
 * <p>治理语义：</p>
 * <ol>
 *   <li><b>限频</b>（可选）：命中即抛 {@link com.smart.trip.common.exception.RateLimitedException}
 *       直接传播，不重试、不降级（边界 #2）；</li>
 *   <li><b>重试 + 指数退避</b>：模型调用失败按 {@link RetryPolicy} 重试，退避 1/2/4s；</li>
 *   <li><b>失败降级</b>：重试耗尽后返回空事件列表（降级响应），由 {@code HarnessAgent} 的
 *       {@code fallbackModel} 提供更高层降级。</li>
 * </ol>
 */
public final class GovernanceMiddleware implements MiddlewareBase {

    private static final String API_LLM = RateLimiter.API_LLM;

    private final GovernedExternalCaller caller;

    /**
     * 构造不含限频的治理中间件。
     *
     * @param retryPolicy 重试与退避策略，不可为 null
     * @param sleeper     退避等待实现，不可为 null
     */
    public GovernanceMiddleware(RetryPolicy retryPolicy, Sleeper sleeper) {
        this(retryPolicy, sleeper, null);
    }

    /**
     * 构造含限频的治理中间件。
     *
     * @param retryPolicy 重试与退避策略，不可为 null
     * @param sleeper     退避等待实现，不可为 null
     * @param rateLimiter 限频器，可为 null（表示不施加限频）
     */
    public GovernanceMiddleware(RetryPolicy retryPolicy, Sleeper sleeper, RateLimiter rateLimiter) {
        this.caller = new GovernedExternalCaller(retryPolicy, sleeper, rateLimiter);
    }

    /**
     * 在模型调用环节施加治理链（限频→重试→降级）。
     *
     * <p>把 {@code next.apply(input)} 的 Reactive {@code Flux<AgentEvent>} 收集为同步
     * {@code List<AgentEvent>}，经 {@link GovernedExternalCaller#call} 统一治理，再以
     * {@code Flux.fromIterable} 发射回 Reactive 管道。降级时返回空 Flux。</p>
     *
     * @param agent 当前 Agent 实例（可为 null，治理链不使用）
     * @param input 模型调用输入
     * @param next  下游模型调用管道
     * @return 治理后的事件流
     */
    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext runtimeContext,
            ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {
        // 内部委托 GovernedExternalCaller.call()，而非复制治理代码
        CallResult<List<AgentEvent>> result = caller.call(
                API_LLM,
                () -> next.apply(input).collectList().block(),
                cause -> List.of()); // 降级：返回空事件
        return Flux.fromIterable(result.value());
    }
}

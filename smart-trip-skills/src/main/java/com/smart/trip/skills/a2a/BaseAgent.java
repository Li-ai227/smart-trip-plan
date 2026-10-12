package com.smart.trip.skills.a2a;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.InvalidRequestException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 所有子 Agent 的 A2A 协同基类（C-005 AC-2/AC-5）。
 *
 * <p>统一封装：Agent 标识（{@code agent://<id>}）、生命周期状态（{@link AgentStatus}）、
 * A2A 收发与失败处理。子 Agent 仅实现业务回调 {@link #onTaskAssign(Msg)}，协同契约由本类保证：</p>
 * <ul>
 *   <li>仅受理 {@code TASK_ASSIGN}，否则快速失败（编码规范 §5）；</li>
 *   <li>正常/降级产出统一装配为 {@code TASK_RESULT}，降级显式 {@code fallback=true}（§2.2/§2.3）；</li>
 *   <li>业务回调抛错时返回可识别 {@code TASK_ERROR}，禁止静默超时（接口协议.md §2 测试关注点）；</li>
 *   <li>{@code traceId} 自委派消息透传，贯穿全链路（§5）。</li>
 * </ul>
 *
 * <p>遵守 Agent 无状态原则（工程结构.md §2.1）：本类仅持有不可变标识与瞬时生命周期状态，
 * 不缓存业务数据；业务状态由各 Agent 经 Redis/ReMe 承载。</p>
 *
 * @deprecated 自 C-005-B 起，迁移到 {@code io.agentscope.harness.agent.HarnessAgent}（ADR-008）。
 *     HarnessAgent 提供原生 ReAct 推理、中间件链、OTel 追踪，本类仅在并存窗口期保留。
 */
@Deprecated(since = "C-005-B", forRemoval = true)
public abstract class BaseAgent {

    /** 未预期异常归一的可识别错误码（区别于业务 {@link BaseException} 的语义码）。 */
    public static final String ERROR_CODE_AGENT_FAILURE = "AGENT_FAILURE";

    /** Agent 唯一标识，构造后不可变。 */
    private final AgentId agentId;

    /** 瞬时生命周期状态（可观测用），原子持有以保证跨线程可见性。 */
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.IDLE);

    /**
     * 构造子 Agent 基类。
     *
     * @param agentId Agent 唯一标识，不可为 null
     */
    protected BaseAgent(AgentId agentId) {
        if (agentId == null) {
            throw new InvalidRequestException("BaseAgent agentId 不可为 null");
        }
        this.agentId = agentId;
    }

    /**
     * 返回 Agent 唯一标识。
     *
     * @return Agent 标识
     */
    public AgentId agentId() {
        return agentId;
    }

    /**
     * 返回当前生命周期状态。
     *
     * @return Agent 运行状态
     */
    public AgentStatus status() {
        return status.get();
    }

    /**
     * 受理一条委派消息并返回结果消息（A2A 收发统一入口）。
     *
     * @param assign 委派消息，必须为 {@code TASK_ASSIGN}
     * @return {@code TASK_RESULT}（正常/降级）或 {@code TASK_ERROR}（失败），{@code traceId} 透传
     * @throws InvalidRequestException 当消息为 null 或类型非 {@code TASK_ASSIGN}
     */
    public final Msg receive(Msg assign) {
        if (assign == null || assign.type() != MessageType.TASK_ASSIGN) {
            throw new InvalidRequestException("BaseAgent 仅受理 TASK_ASSIGN: " + typeOf(assign));
        }
        status.set(AgentStatus.THINKING);
        try {
            status.set(AgentStatus.ACTING);
            AgentReply reply = onTaskAssign(assign);
            return Msg.taskResult(agentId.uri(), assign.from(), assign.traceId(),
                    reply.result(), reply.fallback());
        } catch (BaseException ex) {
            return Msg.taskError(agentId.uri(), assign.from(), assign.traceId(),
                    ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            return Msg.taskError(agentId.uri(), assign.from(), assign.traceId(),
                    ERROR_CODE_AGENT_FAILURE, String.valueOf(ex.getMessage()));
        } finally {
            status.set(AgentStatus.DONE);
        }
    }

    /**
     * 子 Agent 业务回调：处理委派载荷并产出结果。
     *
     * <p>未知/新增 payload 字段应忽略而非失败（向后兼容，§2.3 / 边界 #4）。降级场景应返回
     * {@link AgentReply#fallback(Object)}；不可恢复失败应抛 {@link BaseException} 以携带可识别错误码。</p>
     *
     * @param assign 委派消息（已校验为 {@code TASK_ASSIGN}）
     * @return 业务产出（正常或降级）
     */
    protected abstract AgentReply onTaskAssign(Msg assign);

    private static String typeOf(Msg msg) {
        return msg == null ? "null" : String.valueOf(msg.type());
    }
}

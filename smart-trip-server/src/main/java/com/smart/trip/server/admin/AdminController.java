package com.smart.trip.server.admin;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.model.AuthPrincipal;
import com.smart.trip.server.plan.facade.TripPlanFacade;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端点（C-011 AC-6 §1.5）。
 *
 * <p>复用 {@link AgentStatusService}（C-005 产物）提供 Agent 状态；
 * 通过 {@link TripPlanFacade} 提供跳过数据隔离的管理员规划查询。
 * 所有端点需 ADMIN 角色（SecurityConfig 已全局配置 + Controller 内双重守护）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AgentStatusService agentStatusService;
    private final TripPlanFacade tripPlanFacade;

    /**
     * 构造管理控制器。
     *
     * @param agentStatusService Agent 状态服务
     * @param tripPlanFacade     规划门面
     */
    public AdminController(AgentStatusService agentStatusService, TripPlanFacade tripPlanFacade) {
        this.agentStatusService = agentStatusService;
        this.tripPlanFacade = tripPlanFacade;
    }

    /**
     * 查询所有 Agent 状态（AC-6/Case-11 §1.5）。
     *
     * @return 200 + Agent 状态列表
     */
    @GetMapping("/agent/status")
    public ResponseEntity<List<AgentStatusView>> agentStatus() {
        requireAdmin();
        return ResponseEntity.ok(agentStatusService.currentStatus());
    }

    /**
     * 管理员查询任意规划详情，跳过 userId 数据隔离（AC-6 §1.5）。
     *
     * @param planId 方案 ID
     * @return 200 + 规划详情
     */
    @GetMapping("/plan/{planId}")
    public ResponseEntity<Map<String, Object>> planDetail(@PathVariable("planId") String planId) {
        requireAdmin();
        return ResponseEntity.ok(tripPlanFacade.queryPlanAsAdmin(planId));
    }

    private static void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        if (!"ADMIN".equals(principal.role())) {
            throw new BaseException(ErrorCode.FORBIDDEN, "需要 ADMIN 角色");
        }
    }
}

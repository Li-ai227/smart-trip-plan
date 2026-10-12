package com.smart.trip.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.trip.server.config.TestAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 健康端点冒烟测试（C-003 AC-2/AC-5）。
 *
 * <p>以 test profile 经 MockMvc 验证 {@code GET /actuator/health} 返回 200 且 {@code status=UP}。
 * MockMvc 由 WebApplicationContext 直接构建（仅依赖 spring-test，不引额外测试装配模块）。
 * 真实 Redis/Nacos 实例级聚合（DOWN 标组件）属运行期验证，因本机无 Docker 登记为环境阻塞。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthConfig.class)
class HealthEndpointTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

package com.smart.trip.server;

import com.smart.trip.server.config.TestAuthConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用上下文冒烟测试（C-003 AC-2/AC-6）。
 *
 * <p>以 test profile 验证 Spring 上下文可空跑加载：Redis 自动装配被排除、Nacos 探测关闭、
 * 环境变量校验关闭，故 CI 不依赖外部容器即可启动。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthConfig.class)
class ServerApplicationContextTest {

    @Test
    void contextLoads() {
        // 上下文成功加载即通过；装配/配置异常会使本测试失败。
    }
}

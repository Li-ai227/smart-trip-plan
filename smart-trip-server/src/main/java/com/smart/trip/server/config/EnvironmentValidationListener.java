package com.smart.trip.server.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 启动期环境变量 fail-fast 监听器（C-003 AC-3/AC-4）。
 *
 * <p>在环境就绪（含 profile 与 {@code application-*.yml} 已加载）后触发，委派
 * {@link EnvironmentValidator} 校验必需密钥，缺失则抛异常在 bean 创建前终止启动，
 * 而非运行期才报错。设为最低优先级以确保配置数据已加载。</p>
 */
public class EnvironmentValidationListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        boolean enabled = environment.getProperty("trip.env.validation.enabled", Boolean.class, true);
        EnvironmentValidator.validateOrThrow(enabled, environment::getProperty);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

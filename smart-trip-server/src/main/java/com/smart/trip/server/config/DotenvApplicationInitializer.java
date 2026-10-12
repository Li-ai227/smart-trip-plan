package com.smart.trip.server.config;

import java.util.Properties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * 启动期 .env 加载器（ApplicationContextInitializer）。
 *
 * <p>在 Spring Environment 就绪后、Bean 创建前，读取项目根目录的 {@code .env}
 * 文件并注入为最低优先级的 {@code PropertySource}（名称为 {@code dotenv}）。
 * 系统环境变量（OS env）已在 Spring 默认的 {@code systemEnvironment} source 中，
 * 优先级高于 {@code dotenv}，因此 .env 仅作兜底，不覆盖已存在的系统环境变量。</p>
 *
 * <p>注册方式：{@code spring.factories} 或 {@code application.yml} 中的
 * {@code context.initializer.classes}。</p>
 *
 * <p>安全红线（编码规范 §7）：.env 已在 .gitignore，本类不触碰任何明文密钥。</p>
 */
public class DotenvApplicationInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** PropertySource 名称，供测试与诊断引用。 */
    public static final String DOTENV_SOURCE_NAME = "dotenv";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Properties dotenvProps = DotenvLoader.load();

        if (dotenvProps.isEmpty()) {
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();
        // 添加为最低优先级（系统环境变量优先）
        sources.addLast(new MapPropertySource(DOTENV_SOURCE_NAME,
                (java.util.Map) dotenvProps));
    }
}

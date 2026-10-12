package com.smart.trip.skills.env;

/**
 * 环境变量访问抽象（ADR-009 补充：统一 System.getenv() 与 Spring Environment）。
 *
 * <p>提供统一的环境变量读取入口，使 {@code *HarnessAgentFactory.fromEnvironment()}
 * 和 {@code PlanConfig} 中的密钥读取路径均可通过统一接口获取环境变量值，
 * 从而实现 {@code .env} 文件 → Spring Environment → EnvAccessor → 业务代码 的完整链路。</p>
 *
 * <p>默认提供静态工厂 {@link #fromSystemEnv()} 直接读 {@link System#getenv()}
 * （非 Spring 上下文回退）。Spring 集成端（server 模块）可通过
 * {@code env -> environment.getProperty(env)} 构造 Spring Environment 访问器。</p>
 */
@FunctionalInterface
public interface EnvAccessor {

    /**
     * 读取环境变量值。
     *
     * @param name 环境变量名，不可为 null
     * @return 变量值，不存在时返回 null
     */
    String get(String name);

    /** 直接读 {@link System#getenv()} 的访问器（非 Spring 上下文回退）。 */
    static EnvAccessor fromSystemEnv() {
        return System::getenv;
    }
}

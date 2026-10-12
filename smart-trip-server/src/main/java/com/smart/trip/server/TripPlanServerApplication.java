package com.smart.trip.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * smart-trip-plan Web 服务启动类（C-003）。
 *
 * <p>提供可空跑启动的 Spring Boot 入口，暴露 {@code /actuator/health|info|metrics}。
 * 仅做入口/门面/编排，不承载 Agent 业务规则（工程结构.md §2.3 / R6）。</p>
 */
@SpringBootApplication
public class TripPlanServerApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TripPlanServerApplication.class, args);
    }
}

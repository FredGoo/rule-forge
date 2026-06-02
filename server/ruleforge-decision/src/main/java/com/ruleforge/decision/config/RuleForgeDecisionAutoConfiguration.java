package com.ruleforge.decision.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * RuleForge Decision 模块的 AutoConfiguration。
 *
 * <p>Spring Boot 4 的 ClassPathScanner 默认不扫 BOOT-INF/lib/*.jar 里的 @Component 类,
 * 我们必须显式 @Import 这个 config 才能让 decision 模块的 @Service/@Component
 * 类被 Spring 拾取。
 *
 * <p>包扫描范围:
 * <ul>
 *   <li>com.ruleforge.decision.service.impl — 所有 Service 实现
 *   <li>com.ruleforge.decision.connector — 连接器(数据源抽象)
 *   <li>com.ruleforge.decision.mapper — MyBatis mapper(若需要显式扫)
 * </ul>
 */
@Configuration
@ComponentScan(basePackages = {
        "com.ruleforge.decision.service.impl",
        "com.ruleforge.decision.connector"
})
public class RuleForgeDecisionAutoConfiguration {
}

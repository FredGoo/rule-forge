package com.ruleforge.executor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"com.ruleforge.executor", "com.ruleforge.decision"})
public class RuleForgeExecutorApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleForgeExecutorApplication.class, args);
    }
}

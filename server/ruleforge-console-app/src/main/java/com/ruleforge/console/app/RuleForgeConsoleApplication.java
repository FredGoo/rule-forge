package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"com.ruleforge.console", "com.ruleforge.decision"})
public class RuleForgeConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleForgeConsoleApplication.class, args);
    }
}
package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.ruleforge.console.config.RuleForgeConsoleAutoConfiguration;
import com.ruleforge.console.storage.impl.DatabaseProjectStorageServiceImpl;
import com.ruleforge.decision.config.RuleForgeDecisionAutoConfiguration;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
// Spring Boot 4 不深入扫 BOOT-INF/lib/*.jar 里的 @Component 类,
// 只能显式 @Import 来强制加载规则 console / decision 模块的入口。
// 内部 @ComponentScan 同样不会从 nested jar 拾取子包,需要按需逐个加。
@ImportAutoConfiguration({
        RuleForgeConsoleAutoConfiguration.class,
        RuleForgeDecisionAutoConfiguration.class
})
@Import(DatabaseProjectStorageServiceImpl.class)
public class RuleForgeConsoleApplication {

    public static void main(String[] main) {
        SpringApplication.run(RuleForgeConsoleApplication.class, main);
    }
}

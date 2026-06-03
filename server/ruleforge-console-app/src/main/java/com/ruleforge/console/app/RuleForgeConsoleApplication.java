package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
// 把原 ruleforge-console 模块的 208 个文件全部合进 console-app,
// 控制器/service/mapper/storage/repository 都在 com.ruleforge.console.* 包下,
// @SpringBootApplication 默认从本类的 com.ruleforge.console.app 包开始扫,
// 同时覆盖到 com.ruleforge.console 父包,所以不需要再 @Import 旁路。
// 决策模块通过 spring-boot-maven-plugin 处理,见 META-INF/spring/...imports。
public class RuleForgeConsoleApplication {

    public static void main(String[] main) {
        SpringApplication.run(RuleForgeConsoleApplication.class, main);
    }
}

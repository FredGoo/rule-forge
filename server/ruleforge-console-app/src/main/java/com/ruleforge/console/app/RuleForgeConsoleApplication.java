package com.ruleforge.console.app;

import com.ruleforge.console.config.RuleForgeConsoleAutoConfiguration;
import com.ruleforge.console.controller.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;


// @ComponentScan 故意不带 — 让 @SpringBootApplication 默认扫自己所在的 com.ruleforge.console.app
// Spring Boot 4 改了 ClassPathScanner 默认行为,显式 basePackages 在某些场景下
// 不会深入 BOOT-INF/lib/*.jar。
// 这里用 @Import 显式拉起 RuleForgeConsoleAutoConfiguration(auto-config)
// 以及所有 Controller 类的反射加载,确保这些类被实例化。
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Import({
        RuleForgeConsoleAutoConfiguration.class,
        ActionEditorController.class,
        ApiController.class,
        ApprovalController.class,
        ClientConfigController.class,
        CommonController.class,
        CrosstabEditorController.class,
        DeploymentController.class,
        FrameController.class,
        LoadKnowledgeController.class,
        LoginController.class,
        PackageController.class,
        PermissionController.class,
        ReteDiagramController.class,
        ULEditorController.class,
        VariableController.class,
        XmlController.class
})
public class RuleForgeConsoleApplication {

    public static void main(String[] main) {
        SpringApplication.run(RuleForgeConsoleApplication.class, main);
    }
}

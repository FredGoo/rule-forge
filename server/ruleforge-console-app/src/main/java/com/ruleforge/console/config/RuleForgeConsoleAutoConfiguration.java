package com.ruleforge.console.config;

import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.service.impl.DefaultRepositoryInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;


/**
 * @author fred
 * @since 2021/11/09 7:23 PM
 */
@Configuration
@PropertySource("classpath:ruleforge-console-context.properties")
@ImportResource("classpath:ruleforge-console-context.xml")
@ComponentScan(basePackages = {
        "com.ruleforge.console.config",
        "com.ruleforge.console.controller",
        "com.ruleforge.console.service",
        "com.ruleforge.console.service.impl",
        "com.ruleforge.console.repository",
        "com.ruleforge.console.storage",
        "com.ruleforge.console.storage.impl",
        "com.ruleforge.console.flow",
        "com.ruleforge.console.model"
})
@MapperScan(basePackages = {
        "com.ruleforge.console.mapper"
})
public class RuleForgeConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepositoryInterceptor repositoryInterceptor() {
        return new DefaultRepositoryInterceptor();
    }

}

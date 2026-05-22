package com.ruleforge.console.app.config;

import com.ruleforge.console.servlet.RuleForgeServlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class RuleForgeConfiguration {

    @Bean
    public ServletRegistrationBean registerRuleForgeServlet() {
        return new ServletRegistrationBean(new RuleForgeServlet(), "/ruleforge/*");
    }
}

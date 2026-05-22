package com.ruleforge.console.app.lazy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 延迟加载配置类
 */
@Slf4j
@Configuration
public class LazyLoadConfig {

    @Value("${lazy-load.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${lazy-load.timeout:5000}")
    private int timeout;

    @Bean
    public DataSourceProvider dataSourceProvider(RestTemplate restTemplate) {
        log.info("Initializing RestDataSourceProvider with baseUrl: {}", baseUrl);

        RestDataSourceProvider provider = new RestDataSourceProvider(restTemplate, baseUrl);

        // 可以添加一些默认请求头
        // provider.addDefaultHeader("Authorization", "Bearer xxx");

        return provider;
    }

    @Bean
    @ConditionalOnProperty(name = "lazy-load.enabled", havingValue = "true")
    public LazyEntityFactory lazyEntityFactory(DataSourceProvider dataSourceProvider) {
        return new LazyEntityFactory(dataSourceProvider);
    }
}

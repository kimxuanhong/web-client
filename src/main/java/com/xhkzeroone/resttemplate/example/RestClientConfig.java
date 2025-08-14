package com.xhkzeroone.resttemplate.example;


import com.xhkzeroone.resttemplate.client.LoggingMiddleware;
import com.xhkzeroone.resttemplate.client.RestClient;
import com.xhkzeroone.resttemplate.client.RestConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientConfig {
    private final ApplicationContext ctx;

    public RestClientConfig(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Bean
    @ConfigurationProperties(value = "clients.user-service")
    public RestConfig userServiceConfig() {
        return new RestConfig();
    }

    @Bean
    public RestClient userServiceClient(RestConfig config) {
        RestClient client = new RestClient(ctx, config);
        client.use(LoggingMiddleware.class);
        client.use(LoggingMiddleware.class);
        return client;
    }
}

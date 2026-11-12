package io.clueterra.api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clueterra.budget-service")
public class BudgetServiceProperties {

    private String baseUrl = "http://localhost:8082";
    private String secret = "";
    private Duration timeout = Duration.ofSeconds(10);
}

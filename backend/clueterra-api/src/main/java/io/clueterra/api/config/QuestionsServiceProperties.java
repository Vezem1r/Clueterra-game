package io.clueterra.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clueterra.questions-service")
public class QuestionsServiceProperties {

    private String baseUrl = "http://localhost:8081";
    private String secret = "";
}

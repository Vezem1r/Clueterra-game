package io.clueterra.questions.config;

import io.clueterra.questions.ai.config.AiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({AppProperties.class, AiProperties.class})
public class AppConfig {
}

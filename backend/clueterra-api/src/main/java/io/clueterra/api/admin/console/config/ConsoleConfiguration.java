package io.clueterra.api.admin.console.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsoleProperties.class)
public class ConsoleConfiguration {
}

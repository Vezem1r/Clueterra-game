package io.clueterra.budget.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class BudgetStorageConfig {

    private final BudgetStorageProperties properties;

    @Bean
    @ConditionalOnProperty(
            prefix = "clueterra.storage",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MinioClient budgetMinioClient() {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}

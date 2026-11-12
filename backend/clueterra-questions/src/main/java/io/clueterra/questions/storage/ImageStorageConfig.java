package io.clueterra.questions.storage;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clueterra.storage", name = "enabled", havingValue = "true")
public class ImageStorageConfig {

    private final ImageStorageProperties properties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}

package io.clueterra.budget.storage;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import io.clueterra.budget.image.DownloadedBudgetImage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetImageStorageService {

    public static final String BUCKET = "budget-images";

    private final ObjectProvider<MinioClient> minioClientProvider;
    private final BudgetStorageProperties properties;

    public void store(String objectKey, DownloadedBudgetImage image) {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (!properties.enabled() || client == null) {
            throw new IllegalStateException("Budget image storage is disabled");
        }
        try (ByteArrayInputStream stream = new ByteArrayInputStream(image.bytes())) {
            client.putObject(
                    PutObjectArgs.builder().bucket(BUCKET).object(objectKey).stream(
                                    stream, image.bytes().length, -1)
                            .contentType(image.contentType())
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store BudgetGuessr image", exception);
        }
    }

    public Optional<String> contentType(String objectKey) {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (!properties.enabled() || client == null) {
            return Optional.empty();
        }
        try {
            String contentType =
                    client
                            .statObject(StatObjectArgs.builder().bucket(BUCKET).object(objectKey).build())
                            .contentType();
            return Optional.ofNullable(contentType).filter(value -> !value.isBlank());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IllegalStateException("Failed to inspect BudgetGuessr image", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to inspect BudgetGuessr image", exception);
        }
    }
}

package io.clueterra.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "clueterra.storage")
public class StorageProperties {

    private String endpoint;
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private long maxFileSize;
    private long maxHintImageSize;
}

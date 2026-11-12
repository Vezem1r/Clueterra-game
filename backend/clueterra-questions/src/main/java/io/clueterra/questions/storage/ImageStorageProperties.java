package io.clueterra.questions.storage;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clueterra.storage")
@Getter
@Setter
public class ImageStorageProperties {

    private String endpoint = "http://localhost:9000";
    private String publicEndpoint = "http://localhost:9000";
    private String accessKey;
    private String secretKey;
    private boolean enabled = false;
    private boolean compressRasterImages = true;
    private int maxWidth = 1024;
    private int maxHeight = 1024;
    private float jpegQuality = 0.82f;
    private long maxDownloadBytes = 50L * 1024L * 1024L;
    private long wikimediaDownloadIntervalMs = 1100;
    private int downloadMaxRetries = 3;
    private long downloadRetryBaseDelayMs = 5000;
    private int orphanGraceHours = 24;
    private double maxUnregisteredSweepFraction = 0.2;
    private List<String> managedBuckets = List.of("hint-images", "budget-images");
    private String maintenanceCron = "0 30 3 * * *";
    private boolean maintenanceSchedulerEnabled = true;
}

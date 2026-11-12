package io.clueterra.questions.clients.common.dto;

import lombok.Data;

@Data
public class SourceProperties {
    private String baseUrl;
    private String queryUrl;
    private String actionApiUrl;
    private int timeoutSeconds;
    private int rateLimitPerSecond;
    private Integer rateLimitPerMinute;
    private boolean enabled;

    // Optional fields
    private Integer yearRange;
    private Long durationOfSeconds;
    private Integer ageRange;
    private Integer batchSize;
    private String username;
    private String apiKey;
    private Boolean warmupEnabled;

    // CIA Factbook client
    private Integer cacheDurationDays;
    private String githubTreeUrl;
    private String githubToken;

}

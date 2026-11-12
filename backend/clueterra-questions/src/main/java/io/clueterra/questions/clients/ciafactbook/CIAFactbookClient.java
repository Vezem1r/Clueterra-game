package io.clueterra.questions.clients.ciafactbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.DynamicMappingsConfig;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CIAFactbookClient implements DataSourceClient {

    private final ObjectMapper objectMapper;
    private final SourcesProperties sourcesProperties;
    private final HttpClientHelper httpClient;
    private final CIAFactbookParser parser;
    private final DynamicMappingsConfig dynamicMappingsConfig;

    private SourceProperties props;
    private String normalizedBaseUrl;
    private Duration timeout;

    private String githubTreeUrl;
    private Cache<String, Map<String, String>> mappingCache;

    private static final String JSON = ".json";

    @PostConstruct
    public void init() {
        this.props = sourcesProperties.getSource(getSource());
        if (!props.isEnabled()) {
            log.info("[{}] Client is disabled", getSource());
            return;
        }

        int cacheDuration = props.getCacheDurationDays() != null ? props.getCacheDurationDays() : 180;
        this.githubTreeUrl = props.getGithubTreeUrl();
        this.mappingCache = Caffeine.newBuilder().expireAfterWrite(cacheDuration, TimeUnit.DAYS).build();
        this.normalizedBaseUrl = httpClient.normalizeBaseUrl(props.getBaseUrl());
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
        httpClient.configureRateLimiter(getSource(), props.getRateLimitPerSecond());

        log.info("=== CIA Factbook Client Initialization ===");
        log.info("[{}] Base URL: {}", getSource(), normalizedBaseUrl);
        log.info("[{}] Rate limit: {} req/sec", getSource(), props.getRateLimitPerSecond());
        log.info("[{}] Timeout: {}s", getSource(), props.getTimeoutSeconds());
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("============================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.CIA_FACTBOOK;
    }

    @Override
    public String getBaseUrl() {
        return normalizedBaseUrl;
    }

    @Override
    public boolean isAvailable() {
        if (!props.isEnabled()) {
            log.debug("[{}] Client is disabled", getSource());
            return false;
        }

        try {
            Map<String, String> structure = getCountryMapping();

            boolean isHealthy = !structure.isEmpty();

            if (isHealthy) {
                log.debug("[{}] Health check OK. Structure is ready.", getSource());
            } else {
                log.warn("[{}] Health check FAILED: Structure is empty", getSource());
            }

            return isHealthy;
        } catch (Exception e) {
            log.error("[{}] Health check exception: {}", getSource(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!props.isEnabled()) {
            log.debug("[{}] Client is disabled, skipping fetch", getSource());
            return List.of();
        }

        Map<String, String> structure = getCountryMapping();
        log.info("[{}] === Fetching data for all countries ===", getSource());

        List<RawCountryData> results = new ArrayList<>();
        int processed = 0;

        for (String code : structure.keySet()) {
            try {
                String isoCode = dynamicMappingsConfig.getCiaFactbookToIso().get(code);
                if (StringUtils.isNotBlank(isoCode)) {
                    fetchCountryByCode(isoCode).ifPresent(results::add);
                } else {
                    log.debug("[{}] Skipping file {}.json - No ISO mapping found", getSource(), code);
                }
                processed++;

                if (processed % 20 == 0) {
                    log.info("[{}] === Progress: {}/{} countries ===", getSource(), processed, structure.size());
                    httpClient.backoff(Duration.ofMillis(200));
                }

            } catch (Exception e) {
                log.warn("[{}] === Fetch FAILED: {} ===", getSource(), code, e);
            }
        }

        log.info("[{}] === Completed: {} countries ===", getSource(), results.size());
        return results;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!props.isEnabled()) {
            return Optional.empty();
        }

        String ciaCode = dynamicMappingsConfig.getCiaIsoToFactbook().get(countryCode.toUpperCase());
        if (ciaCode == null) {
            log.warn("[{}] Mapping not found for ISO code: {}", getSource(), countryCode);
            return Optional.empty();
        }

        Map<String, String> structure = getCountryMapping();
        String path = structure.get(ciaCode);

        if (path == null) {
            log.warn("[{}] Mapping not found for code: {}", getSource(), ciaCode);
            return Optional.empty();
        }

        String url = normalizedBaseUrl + "/" + path + JSON;
        log.debug("[{}] Fetching country data: {}", getSource(), ciaCode);

        return httpClient.getOptional(getSource(), url, timeout)
                .map(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        List<RawStatistic> stats = parser.parseAll(root);
                        String countryName = deriveCountryName(root, countryCode.toUpperCase(Locale.ROOT));
                        log.debug("[{}] Parsed {} facts for {}", getSource(), stats.size(), ciaCode);

                        return RawCountryData.builder()
                                .isoAlpha2(countryCode.toUpperCase())
                                .nameEn(countryName)
                                .source(getSource())
                                .statistics(stats)
                                .build();
                    } catch (Exception e) {
                        log.error("[{}] Failed to parse country {}", getSource(), ciaCode, e);
                        return null;
                    }
                });
    }

    static String deriveCountryName(JsonNode root, String isoAlpha2) {
        JsonNode countryName = root.path("Government").path("Country name");
        String extracted = firstNonBlank(
                extractText(countryName.path("conventional short form")),
                extractText(countryName.path("conventional long form")),
                extractText(countryName.path("local short form")),
                extractText(countryName.path("local long form")),
                extractText(countryName)
        );

        if (StringUtils.isNotBlank(extracted)) {
            return extracted;
        }

        if (StringUtils.isBlank(isoAlpha2)) {
            return null;
        }

        String localeName = new Locale.Builder().setRegion(isoAlpha2).build().getDisplayCountry(Locale.ENGLISH);
        return StringUtils.isNotBlank(localeName) && !localeName.equalsIgnoreCase(isoAlpha2)
                ? localeName
                : null;
    }

    private static String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return StringUtils.trimToNull(node.asText());
        }
        JsonNode textNode = node.path("text");
        if (!textNode.isMissingNode() && textNode.isTextual()) {
            return StringUtils.trimToNull(textNode.asText());
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> getCountryMapping() {
        return mappingCache.get("CIA_STRUCTURE_MAP", key -> {
            log.info("[{}] Fetching full repository structure...", getSource());
            Map<String, String> ciaToPath = new HashMap<>();

            httpClient.getOptional(getSource(), githubTreeUrl, timeout, buildGithubHeaders())
                    .ifPresent(treeJson -> {
                        try {
                            JsonNode root = objectMapper.readTree(treeJson);
                            JsonNode tree = root.path("tree");

                            if (tree.isArray()) {
                                for (JsonNode node : tree) {
                                    String path = node.path("path").asText();
                                    String type = node.path("type").asText();

                                    if ("blob".equals(type) && path.endsWith(JSON) && path.contains("/")) {
                                        String[] parts = path.split("/");
                                        String region = parts[0];
                                        String fileName = parts[1];

                                        if (!isServiceFolder(region)) {
                                            String code = fileName.replace(JSON, "").toLowerCase();
                                            ciaToPath.put(code, region + "/" + code);
                                        }
                                    }
                                }
                            }
                            log.info("[{}] Registered {} countries", getSource(), ciaToPath.size());
                        } catch (Exception e) {
                            log.error("[{}] Structure discovery failed", getSource(), e);
                        }
                    });

            return ciaToPath;
        });
    }

    private boolean isServiceFolder(String name) {
        return name.startsWith(".") || List.of("world", "metadata", "factbook.json").contains(name);
    }

    private Map<String, String> buildGithubHeaders() {
        if (props == null) {
            return Map.of();
        }

        String token = props.getGithubToken();

        if (token == null || token.isBlank()) {
            return Map.of();
        }

        return Map.of("Authorization", "Bearer " + token);
    }
}

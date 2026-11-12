package io.clueterra.questions.clients.owid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.owid.dto.OwidDataset;
import io.clueterra.questions.clients.owid.dto.OwidLicensePolicy;
import io.clueterra.questions.entity.enums.DataSource;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwidMetadataValidator {

    private static final String TRUSTED_METADATA_HOST = "api.ourworldindata.org";

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;

    public boolean isAllowed(OwidDataset dataset, Duration timeout) {
        try {
            Optional<String> chartJson = httpClient.getOptional(
                    DataSource.OUR_WORLD_IN_DATA, dataset.metadataUrl(), timeout);
            if (chartJson.isEmpty()) {
                return reject(dataset, "chart metadata is unavailable");
            }

            JsonNode chartMetadata = objectMapper.readTree(chartJson.get());
            JsonNode columnMetadata = chartMetadata.path("columns").get(dataset.metadataColumn());
            if (columnMetadata == null || columnMetadata.isMissingNode()) {
                return reject(dataset, "reviewed metadata column is missing");
            }

            OwidLicensePolicy policy = dataset.licensePolicy();
            String citation = columnMetadata.path("citationShort").asText("");
            if (!policy.attribution().equals(citation)) {
                return reject(dataset, "source attribution changed");
            }

            String fullMetadataUrl = columnMetadata.path("fullMetadata").asText("");
            if (!isTrustedMetadataUrl(fullMetadataUrl)) {
                return reject(dataset, "full metadata URL is missing or untrusted");
            }

            Optional<String> fullJson = httpClient.getOptional(
                    DataSource.OUR_WORLD_IN_DATA, fullMetadataUrl, timeout);
            if (fullJson.isEmpty()) {
                return reject(dataset, "full metadata is unavailable");
            }

            JsonNode fullMetadata = objectMapper.readTree(fullJson.get());
            if (fullMetadata.path("nonRedistributable").asBoolean(true)) {
                return reject(dataset, "dataset is marked as non-redistributable");
            }

            String processingLevel = fullMetadata.path("processingLevel").asText("");
            if (!policy.approvedProcessingLevels().contains(processingLevel)) {
                return reject(dataset, "processing level changed to '" + processingLevel + "'");
            }

            JsonNode origins = fullMetadata.path("origins");
            Set<String> actualProducers = textValues(origins);
            if (!actualProducers.equals(policy.expectedProducers())) {
                return reject(dataset, "origin providers changed");
            }

            if (!policy.reliesOnOwidMajorProcessing()) {
                Set<String> actualLicenseUrls = licenseUrls(origins);
                if (!actualLicenseUrls.equals(policy.approvedOriginLicenseUrls())) {
                    return reject(dataset, "origin license changed");
                }
            }

            return true;
        } catch (DataSourceException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                    "[OWID] Rejected {} because metadata validation failed: {}",
                    dataset.indicatorCode(),
                    e.getMessage());
            return false;
        }
    }

    private Set<String> textValues(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return Set.of();
        }
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(node -> node.path("producer").asText(""))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> licenseUrls(JsonNode origins) {
        if (!origins.isArray()) {
            return Set.of();
        }
        return StreamSupport.stream(origins.spliterator(), false)
                .map(node -> node.path("license").path("url").asText(""))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isTrustedMetadataUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && TRUSTED_METADATA_HOST.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean reject(OwidDataset dataset, String reason) {
        log.warn(
                "[OWID] Rejected {}: {}. The dataset needs a new legal review before use.",
                dataset.indicatorCode(),
                reason);
        return false;
    }
}

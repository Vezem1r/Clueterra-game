package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.controller.dto.SourceDetail;
import io.clueterra.questions.controller.dto.SourcesOverview;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.lens.CountryHintLens;
import io.clueterra.questions.service.sync.DataSourceClientRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SourceOverviewService {

    private final DataSourceClientRegistry clientRegistry;
    private final Map<DataSource, List<CountryHintLens>> countryHintLenses;
    private final Clock clock;

    public SourceOverviewService(
            DataSourceClientRegistry clientRegistry,
            List<CountryHintLens> countryHintLenses,
            Clock clock) {
        this.clientRegistry = clientRegistry;
        this.clock = clock;
        this.countryHintLenses = countryHintLenses.stream()
                .collect(Collectors.groupingBy(
                        CountryHintLens::source,
                        () -> new EnumMap<>(DataSource.class),
                        Collectors.toUnmodifiableList()));
    }

    public SourcesOverview getOverview() {
        List<SourceDetail> sources = Arrays.stream(DataSource.values())
                .map(this::createSourceDetail)
                .sorted(Comparator.comparingInt(SourceDetail::priority))
                .toList();
        return new SourcesOverview(
                sources.size(),
                (int) sources.stream().filter(SourceDetail::enabled).count(),
                (int) sources.stream().filter(SourceDetail::healthy).count(),
                sources,
                Instant.now(clock));
    }

    private SourceDetail createSourceDetail(DataSource source) {
        Optional<DataSourceClient> client = clientRegistry.getClient(source);
        List<CountryHintLens> lenses = countryHintLenses.getOrDefault(source, List.of());

        boolean syncAvailable = source.isSyncSupported()
                && client.isPresent()
                && clientRegistry.isEnabled(source);
        boolean syncHealthy = syncAvailable && clientRegistry.isHealthy(source);
        boolean hintSourceConfigured = source.isHintGenerationSupported() && !lenses.isEmpty();
        boolean countryHintAvailable = hintSourceConfigured
                && lenses.stream().anyMatch(CountryHintLens::isAvailable);
        boolean hintGenerationAvailable = source.isHintGenerationSupported()
                && (syncAvailable || countryHintAvailable);
        boolean enabled = syncAvailable || hintSourceConfigured;
        boolean healthy = syncHealthy || countryHintAvailable;

        String status = !enabled ? "DISABLED" : healthy ? "AVAILABLE" : "UNHEALTHY";
        String baseUrl = client.map(DataSourceClient::getBaseUrl).orElse(null);
        Map<String, Object> metadata = buildMetadata(client, baseUrl);
        String message = buildMessage(syncAvailable, syncHealthy, hintSourceConfigured, countryHintAvailable);

        return new SourceDetail(
                source.name(),
                source.getDisplayName(),
                baseUrl,
                source.getPriority(),
                enabled,
                healthy,
                status,
                syncAvailable,
                hintGenerationAvailable,
                metadata,
                message);
    }

    private Map<String, Object> buildMetadata(Optional<DataSourceClient> client, String baseUrl) {
        if (client.isEmpty()) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("clientClass", client.get().getClass().getSimpleName());
        metadata.put("priority", client.get().getPriority());
        metadata.put("baseUrl", baseUrl);
        return metadata;
    }

    private String buildMessage(
            boolean syncAvailable,
            boolean syncHealthy,
            boolean hintSourceConfigured,
            boolean countryHintAvailable) {
        if (syncAvailable) {
            return syncHealthy
                    ? "Source is available and responding"
                    : "Source health check failed";
        }
        if (hintSourceConfigured) {
            return countryHintAvailable
                    ? "Country-level hint source; no external sync required"
                    : "Country-level hint source unavailable; check image storage and assets";
        }
        return "Source is not configured for admin operations";
    }
}

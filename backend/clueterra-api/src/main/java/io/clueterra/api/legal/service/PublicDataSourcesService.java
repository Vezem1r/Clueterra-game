package io.clueterra.api.legal.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.clueterra.api.legal.dto.PublicDataSourceItemResponse;
import io.clueterra.api.legal.dto.PublicDataSourcesResponse;
import io.clueterra.api.legal.model.DataSourceRegistryEntry;
import io.clueterra.api.legal.model.DataSourcesRegistryDocument;
import jakarta.annotation.PostConstruct;

@Service
public class PublicDataSourcesService {

    private static final String DATA_SOURCES_PATH = "legal/data-sources.yml";

    private final ObjectMapper yamlMapper;
    private PublicDataSourcesResponse cachedResponse;

    public PublicDataSourcesService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void loadRegistry() {
        DataSourcesRegistryDocument document = readDocument();
        List<PublicDataSourceItemResponse> sources = document.sources().entrySet().stream()
                .map(this::toResponse)
                .toList();
        cachedResponse = new PublicDataSourcesResponse(document.registryVersion(), sources);
    }

    public PublicDataSourcesResponse getPublicDataSources() {
        return cachedResponse;
    }

    private DataSourcesRegistryDocument readDocument() {
        ClassPathResource resource = new ClassPathResource(DATA_SOURCES_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            DataSourcesRegistryDocument document = yamlMapper.readValue(inputStream, DataSourcesRegistryDocument.class);
            if (document == null || document.sources() == null || document.sources().isEmpty()) {
                throw new IllegalStateException("No public data sources configured");
            }
            return document;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load public data sources registry", e);
        }
    }

    private PublicDataSourceItemResponse toResponse(Map.Entry<String, DataSourceRegistryEntry> entry) {
        DataSourceRegistryEntry source = entry.getValue();
        return new PublicDataSourceItemResponse(
                entry.getKey(),
                source.displayName(),
                source.summary(),
                source.websiteNotice(),
                source.sourceLabel(),
                source.termsUrl(),
                source.licenseLabel(),
                source.licenseUrl(),
                source.publicNote());
    }
}

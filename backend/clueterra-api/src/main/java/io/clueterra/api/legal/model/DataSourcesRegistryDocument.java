package io.clueterra.api.legal.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataSourcesRegistryDocument(
        String registryVersion,
        Map<String, DataSourceRegistryEntry> sources) {
}

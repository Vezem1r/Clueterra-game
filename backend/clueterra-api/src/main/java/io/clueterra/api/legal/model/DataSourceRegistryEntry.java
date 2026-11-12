package io.clueterra.api.legal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataSourceRegistryEntry(
        String reviewStatus,
        String displayName,
        String summary,
        String fetchedData,
        String storagePolicy,
        String imagePolicy,
        String commercialPolicy,
        String websiteNotice,
        String restrictions,
        String sourceLabel,
        String termsUrl,
        String licenseLabel,
        String licenseUrl,
        String publicNote) {
}

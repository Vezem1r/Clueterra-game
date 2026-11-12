package io.clueterra.questions.countrycatalog.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.questions.countrycatalog.model.CountryCatalogBundle;
import io.clueterra.questions.countrycatalog.model.CountryCatalogEntry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CountryCatalogReader {

    private static final String BUNDLE = "assets/country-catalog/catalog.json";
    private static final int BUNDLE_VERSION = 1;

    private final ObjectMapper mapper;

    public List<CountryCatalogEntry> read() throws IOException {
        try (InputStream input = new ClassPathResource(BUNDLE).getInputStream()) {
            CountryCatalogBundle bundle = mapper.readValue(input, CountryCatalogBundle.class);
            if (bundle.version() != BUNDLE_VERSION || bundle.entries() == null || bundle.entries().isEmpty()) {
                throw new IllegalStateException("Bundled country catalog is missing or outdated");
            }
            return bundle.entries();
        }
    }
}

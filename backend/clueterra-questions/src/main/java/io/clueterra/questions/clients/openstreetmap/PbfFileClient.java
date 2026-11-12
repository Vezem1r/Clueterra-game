package io.clueterra.questions.clients.openstreetmap;

import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.AbstractStaticClient;
import io.clueterra.questions.clients.openstreetmap.dto.CountryPbfFile;
import io.clueterra.questions.clients.openstreetmap.dto.OsmCountryStatistic;
import io.clueterra.questions.clients.openstreetmap.dao.OsmCountryStatisticDao;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PbfFileClient extends AbstractStaticClient {

    private final GeofabrikFileScanner fileScanner;
    private final OsmCountryStatisticDao statisticDao;
    private final SourcesProperties sourcesProperties;

    @PostConstruct
    public void init() {
        this.props = sourcesProperties.getSource(getSource());
        if (isDisabled()) {
            log.info("[OSM] Data generation disabled");
            return;
        }

        Path directory = getDataDirectory();
        if (directory == null) {
            log.warn("[OSM] Data directory not configured");
            return;
        }

        processAllPbfFiles(directory);
    }

    private void processAllPbfFiles(Path directory) {
        List<CountryPbfFile> countryFiles = fileScanner.scanDirectory(directory);
        if (countryFiles.isEmpty()) {
            log.info("[OSM] No PBF files found in {}", directory);
            return;
        }

        log.info("[OSM] Processing {} country files", countryFiles.size());
        long startTime = System.currentTimeMillis();

        int processed = 0;
        int failed = 0;

        for (CountryPbfFile countryFile : countryFiles) {
            boolean success = processCountryFile(countryFile);
            if (success) {
                processed++;
            } else {
                failed++;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[OSM] Processing complete: {} succeeded, {} failed in {}s", processed, failed, elapsed / 1000);
    }

    private boolean processCountryFile(CountryPbfFile countryFile) {
        try {
            log.info("[OSM] Processing {} ({}) - {}", countryFile.countryName(), countryFile.countryCode(), countryFile.fileSizeFormatted());

            CountryPbfProcessor processor = new CountryPbfProcessor(countryFile.countryCode(), countryFile.path());
            processor.process();
            List<OsmCountryStatistic> statistics = processor.getCollector().buildStatistics();

            if (statistics.isEmpty()) {
                log.warn("[OSM] No statistics collected for {}", countryFile.countryCode());
                return false;
            }
            statisticDao.saveAll(statistics);
            log.info("[OSM] Saved {} statistics for {} in {}s", statistics.size(), countryFile.countryCode(), processor.getElapsedMs() / 1000);
            return true;

        } catch (Exception e) {
            log.error("[OSM] Failed to process {}: {}", countryFile.countryCode(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        log.info("[OSM] Syncing all countries to main table");
        statisticDao.syncAllToMain();
        return Collections.emptyList();
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (StringUtils.isBlank(countryCode)) {
            log.warn("[OSM] Country code is empty");
            return Optional.empty();
        }

        log.info("[OSM] Syncing {} to main table", countryCode);
        statisticDao.syncCountryToMain(countryCode);
        return Optional.empty();
    }

    private Path getDataDirectory() {
        if (props == null || StringUtils.isBlank(props.getBaseUrl())) {
            return null;
        }
        return Path.of(props.getBaseUrl());
    }

    @Override
    public DataSource getSource() {
        return DataSource.OPEN_STREET_MAP;
    }

    @Override
    public String getBaseUrl() {
        return props != null ? props.getBaseUrl() : null;
    }

    @Override
    public boolean isAvailable() {
        Path dir = getDataDirectory();
        return dir != null && dir.toFile().isDirectory();
    }
}

package io.clueterra.questions.clients.openstreetmap;

import crosby.binary.osmosis.OsmosisReader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CountryPbfProcessor {

    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final long LOG_INTERVAL = 5_000_000;

    private static final Set<String> USEFUL_WAY_KEYS = Set.of(
            "waterway", "natural", "water", "leisure", "landuse", "tourism",
            "amenity", "historic", "aeroway", "man_made", "generator:source",
            "religion", "aerialway", "barrier", "power", "office", "railway"
    );

    @Getter
    private final CountryStatsCollector collector;
    private final String countryCode;
    private final Path filePath;
    private final long startTime;

    public CountryPbfProcessor(String countryCode, Path filePath) {
        this.countryCode = countryCode;
        this.filePath = filePath;
        this.collector = new CountryStatsCollector(countryCode);
        this.startTime = System.currentTimeMillis();
    }

    public void process() {
        log.info("[{}] Starting OSM processing...", countryCode);
        AtomicLong nodeCounter = new AtomicLong();
        AtomicLong wayCounter = new AtomicLong();

        try (InputStream input = new BufferedInputStream(new FileInputStream(filePath.toFile()), BUFFER_SIZE)) {
            OsmosisReader reader = new OsmosisReader(input);
            reader.setSink(new Sink() {
                @Override
                public void process(EntityContainer ec) {
                    if (ec instanceof NodeContainer nc) {
                        long count = nodeCounter.incrementAndGet();
                        if (count % LOG_INTERVAL == 0) {
                            log.info("[{}] Processed {} nodes...", countryCode, formatNumber(count));
                        }
                        processNode(nc.getEntity());
                    } else if (ec instanceof WayContainer wc) {
                        long count = wayCounter.incrementAndGet();
                        if (count % LOG_INTERVAL == 0) {
                            log.info("[{}] Processed {} ways...", countryCode, formatNumber(count));
                        }
                        processWay(wc.getEntity());
                    }
                }

                @Override
                public void initialize(Map<String, Object> m) {
                }

                @Override
                public void complete() {
                }

                @Override
                public void close() {
                }
            });
            reader.run();
        } catch (Exception e) {
            log.error("[{}] PBF processing failed", countryCode, e);
        }

        log.info("[{}] Processing complete in {}s. Total: {} nodes, {} ways.", countryCode, getElapsedMs() / 1000, formatNumber(nodeCounter.get()), formatNumber(wayCounter.get()));
        collector.logStatisticsSummary();
    }

    private void processNode(Node node) {
        if (node.getTags().isEmpty()) {
            return;
        }
        Map<String, String> tags = extractTags(node);
        collector.processNode(tags, node.getId());
    }

    private void processWay(Way way) {
        if (!isUsefulWay(way)) {
            return;
        }
        Map<String, String> tags = extractTags(way);
        collector.processWay(tags, way.getId(), Collections.emptyList());
    }

    private boolean isUsefulWay(Way way) {
        for (Tag tag : way.getTags()) {
            if (USEFUL_WAY_KEYS.contains(tag.getKey())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> extractTags(Way way) {
        Map<String, String> tags = HashMap.newHashMap(way.getTags().size());
        for (Tag tag : way.getTags()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        return tags;
    }

    private Map<String, String> extractTags(Node node) {
        Map<String, String> tags = HashMap.newHashMap(node.getTags().size());
        for (Tag tag : node.getTags()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        return tags;
    }

    private String formatNumber(long value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }
}

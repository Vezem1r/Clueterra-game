package io.clueterra.questions.clients.openstreetmap;

import io.clueterra.questions.clients.openstreetmap.dto.NamedFeature;
import io.clueterra.questions.clients.openstreetmap.dto.OsmCountryStatistic;
import io.clueterra.questions.clients.openstreetmap.dto.OsmPatternInfo;
import io.clueterra.questions.clients.openstreetmap.dto.OsmTagMapping;
import io.clueterra.questions.entity.enums.Category;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class CountryStatsCollector {

    @Getter
    private final String countryCode;

    private final Map<OsmTagMapping, Set<String>> processedEntities = new ConcurrentHashMap<>();
    private final Map<OsmTagMapping, AtomicLong> counters = new ConcurrentHashMap<>();

    private final Set<String> processedFeatureKeys = ConcurrentHashMap.newKeySet();
    private final List<NamedFeature> peaks = Collections.synchronizedList(new ArrayList<>());
    private final List<NamedFeature> volcanoes = Collections.synchronizedList(new ArrayList<>());
    private final List<NamedFeature> waterfalls = Collections.synchronizedList(new ArrayList<>());
    private final List<NamedFeature> lakes = Collections.synchronizedList(new ArrayList<>());

    private final Object featuresLock = new Object();
    private final Object trimLock = new Object();

    private static final int MAX_FEATURE_SIZE = 50;
    private static final double TRIM_THRESHOLD = 1.5;

    private final Map<OsmTagMapping, Map<String, OsmPatternInfo>> acceptedPatterns = new ConcurrentHashMap<>();
    private final Map<OsmTagMapping, Map<String, OsmPatternInfo>> rejectedPatterns = new ConcurrentHashMap<>();

    {
        for (OsmTagMapping mapping : OsmTagMapping.values()) {
            counters.put(mapping, new AtomicLong(0));
            processedEntities.put(mapping, ConcurrentHashMap.newKeySet());
            acceptedPatterns.put(mapping, new ConcurrentHashMap<>());
            rejectedPatterns.put(mapping, new ConcurrentHashMap<>());
        }
    }

    public void processNode(Map<String, String> tags, long osmId) {
        if (!OsmDataValidator.isActive(tags)) {
            return;
        }
        processCounters(tags, "N" + osmId);
        processNamedFeatures(tags, 0.0);
    }

    public void processWay(Map<String, String> tags, long osmId, List<double[]> coordinates) {
        if (!OsmDataValidator.isActive(tags)) {
            return;
        }
        processCounters(tags, "W" + osmId);

        double area = 0.0;
        if (coordinates != null && coordinates.size() > 2) {
            area = calculatePolygonArea(coordinates);
        }

        processNamedFeatures(tags, area);
    }

    private void processCounters(Map<String, String> tags, String entityId) {
        for (OsmTagMapping mapping : OsmTagMapping.values()) {
            boolean matches = matchesMapping(tags, mapping);
            String name = StringUtils.firstNonBlank(tags.get("name:en"), tags.get("name"), tags.get("ref"), "unnamed");

            if (matches) {
                String pattern = extractPattern(tags, mapping);

                if (!mapping.validate(tags)) {
                    trackPattern(rejectedPatterns.get(mapping), pattern, name, entityId);
                } else {
                    String dedupKey = buildDedupKey(tags, mapping, entityId);

                    if (dedupKey.startsWith("SKIP:")) {
                        trackPattern(rejectedPatterns.get(mapping), "standalone_generator (no plant)", name, entityId);
                    } else {
                        Set<String> processed = processedEntities.get(mapping);
                        if (processed.add(dedupKey)) {
                            counters.get(mapping).incrementAndGet();
                            trackPattern(acceptedPatterns.get(mapping), pattern, name, entityId);
                        }
                    }
                }
            }
        }
    }

    private void trackPattern(Map<String, OsmPatternInfo> patterns, String pattern, String name, String entityId) {
        patterns.compute(pattern, (k, v) -> {
            if (v == null) {
                return new OsmPatternInfo(name, entityId);
            } else {
                v.count().incrementAndGet();
                return v;
            }
        });
    }

    private String extractPattern(Map<String, String> tags, OsmTagMapping mapping) {
        List<String> parts = new ArrayList<>();

        String[] relevantKeys = getRelevantKeys(mapping);
        for (String key : relevantKeys) {
            String val = tags.get(key);
            if (val != null) {
                parts.add(key + "=" + val);
            } else if (isImportantMissingKey(key, mapping)) {
                parts.add(key + "=NULL");
            }
        }

        if (parts.isEmpty()) {
            parts.add("no_relevant_tags");
        }

        return String.join(", ", parts);
    }

    private String[] getRelevantKeys(OsmTagMapping mapping) {
        return switch (mapping) {
            case ZOO -> new String[]{"zoo", "attraction", "shop", "amenity"};
            case AQUARIUM, MUSEUM -> new String[]{"shop", "amenity"};
            case AERODROME -> new String[]{"aerodrome", "type", "access", "icao", "iata"};
            case HELIPAD -> new String[]{"access", "usage"};
            case RAILWAY_STATION -> new String[]{"station", "usage"};
            case VOLCANO -> new String[]{"volcano:status", "volcano:type"};
            case RUINS -> new String[]{"building", "ruins"};
            case VIEWPOINT, PEAK, SADDLE, CAVE, PIER -> new String[]{"access"};
            case NUCLEAR, WIND, SOLAR, COAL_PLANT, HYDRO ->
                    new String[]{"power", "operator", "plant:name", "site:name"};
            case MOSQUE, SYNAGOGUE, HINDU, BUDDHIST, SIKH -> new String[]{"religion", "amenity"};
            case CASTLE, FORT -> new String[]{"castle_type", "historic"};
            case PRISON, COURTHOUSE -> new String[]{"historic", "amenity"};
            case EMBASSY -> new String[]{"diplomatic", "office"};
            case GOLF_COURSE -> new String[]{"golf"};
            case THEME_PARK -> new String[]{"leisure", "attraction", "sport"};
            case HOT_SPRING -> new String[]{"man_made", "natural"};
            case BEACH -> new String[]{"leisure", "natural"};
            case LIGHTHOUSE -> new String[]{"seamark:type", "tourism", "historic"};
            default -> new String[]{"access", "type"};
        };
    }

    private boolean isImportantMissingKey(String key, OsmTagMapping mapping) {
        if (mapping == OsmTagMapping.AERODROME && (key.equals("icao") || key.equals("iata"))) {
            return true;
        }
        return mapping.requiresNameGrouping() && (key.equals("operator") || key.equals("plant:name"));
    }

    private boolean matchesMapping(Map<String, String> tags, OsmTagMapping mapping) {
        String tagValue = tags.get(mapping.getOsmKey());
        if (StringUtils.isBlank(tagValue)) {
            return false;
        }

        if (mapping.isExactMatch()) {
            return StringUtils.equalsIgnoreCase(tagValue, mapping.getOsmValue());
        } else {
            return StringUtils.containsIgnoreCase(tagValue, mapping.getOsmValue());
        }
    }

    private String buildDedupKey(Map<String, String> tags, OsmTagMapping mapping, String entityId) {
        if (mapping.requiresNameGrouping()) {
            String plantName = OsmDataValidator.extractPlantName(tags);
            if (plantName != null && plantName.length() >= 3) {
                return "PLANT:" + plantName;
            }
            String power = tags.get("power");
            if ("generator".equals(power)) {
                return "SKIP:" + entityId;
            }
        }

        String name = StringUtils.firstNonBlank(tags.get("name:en"), tags.get("name"));
        if (StringUtils.isNotBlank(name)) {
            String normalizedName = normalizeName(name);
            if (normalizedName.length() >= 3) {
                return "NAME:" + normalizedName;
            }
        }

        String ref = tags.get("ref");
        if (StringUtils.isNotBlank(ref)) {
            String operator = tags.get("operator");
            if (StringUtils.isNotBlank(operator)) {
                return "REF:" + normalizeName(operator) + "_" + ref.toLowerCase().trim();
            }
            return "REF:" + ref.toLowerCase().trim();
        }

        return "ID:" + entityId;
    }

    private void processNamedFeatures(Map<String, String> tags, double areaSqMeters) {
        String name = StringUtils.firstNonBlank(tags.get("name:en"), tags.get("name"));
        if (!OsmDataValidator.isValidName(name)) {
            return;
        }

        String dedupKey = normalizeName(name);

        String natural = tags.get("natural");
        String waterway = tags.get("waterway");
        String water = tags.get("water");

        if ("peak".equals(natural)) {
            String peakKey = "PEAK:" + dedupKey;
            if (processedFeatureKeys.add(peakKey)) {
                Double ele = OsmDataValidator.parseElevation(tags.get("ele"));
                if (ele != null && ele > 0) {
                    addFeature(peaks, new NamedFeature(name.trim(), ele));
                }
            }
        } else if ("volcano".equals(natural)) {
            String volcKey = "VOLC:" + dedupKey;
            if (processedFeatureKeys.add(volcKey)) {
                Double ele = OsmDataValidator.parseElevation(tags.get("ele"));
                if (ele != null && ele > 0) {
                    addFeature(volcanoes, new NamedFeature(name.trim(), ele));
                }
            }
        } else if ("waterfall".equals(waterway)) {
            String fallKey = "FALL:" + dedupKey;
            if (processedFeatureKeys.add(fallKey)) {
                Double height = OsmDataValidator.parseHeight(tags.get("height"));
                if (height != null && height > 0) {
                    addFeature(waterfalls, new NamedFeature(name.trim(), height));
                }
            }
        } else if ("water".equals(natural) || "lake".equals(water) || "reservoir".equals(water)) {
            updateOrAddLake(dedupKey, name.trim(), areaSqMeters);
        }
    }

    private String normalizeName(String name) {
        return StringUtils.stripAccents(name.toLowerCase())
                .replaceAll("\\b(mount|mt|lake|loch|res|reservoir|falls|waterfall|peak|volcano)\\b\\.?", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private void addFeature(List<NamedFeature> list, NamedFeature feature) {
        list.add(feature);

        if (list.size() > MAX_FEATURE_SIZE * TRIM_THRESHOLD) {
            synchronized (trimLock) {
                if (list.size() > MAX_FEATURE_SIZE * TRIM_THRESHOLD) {
                    list.sort(Comparator.comparingDouble(NamedFeature::value).reversed());
                    while (list.size() > MAX_FEATURE_SIZE) {
                        list.removeLast();
                    }
                }
            }
        }
    }

    private void updateOrAddLake(String key, String originalName, double area) {
        if (area <= 0) return;

        synchronized (featuresLock) {
            for (int i = 0; i < lakes.size(); i++) {
                if (normalizeName(lakes.get(i).name()).equals(key)) {
                    NamedFeature existing = lakes.get(i);
                    if (area > existing.value()) {
                        lakes.set(i, new NamedFeature(originalName, area));
                    }
                    return;
                }
            }

            if (lakes.size() < MAX_FEATURE_SIZE || area > getMinLakeArea()) {
                addFeature(lakes, new NamedFeature(originalName, area));
            }
        }
    }

    private double getMinLakeArea() {
        if (lakes.isEmpty()) return 0;
        synchronized (featuresLock) {
            return lakes.stream()
                    .mapToDouble(NamedFeature::value)
                    .min()
                    .orElse(0);
        }
    }

    private double calculatePolygonArea(List<double[]> coords) {
        if (coords.size() < 3) {
            return 0.0;
        }

        double area = 0.0;
        int j = coords.size() - 1;
        for (int i = 0; i < coords.size(); i++) {
            double[] pi = coords.get(i);
            double[] pj = coords.get(j);
            area += (pj[1] + pi[1]) * (pj[0] - pi[0]);
            j = i;
        }

        double centerLat = coords.stream().mapToDouble(c -> c[0]).average().orElse(0);
        double metersPerDegree = 111319.0;
        double latCorrectionFactor = Math.cos(Math.toRadians(centerLat));

        return Math.abs(area / 2.0) * metersPerDegree * metersPerDegree * latCorrectionFactor;
    }

    public List<OsmCountryStatistic> buildStatistics() {
        List<OsmCountryStatistic> results = new ArrayList<>();
        Instant now = Instant.now();

        counters.forEach((mapping, counter) -> {
            long val = counter.get();
            if (val > 0) {
                results.add(OsmCountryStatistic.builder()
                        .countryCode(countryCode)
                        .indicatorCode(mapping.getDbCode())
                        .category(mapping.getCategory())
                        .value(BigDecimal.valueOf(val))
                        .weight(mapping.getWeightBD())
                        .fetchedAt(now)
                        .build());
            }
        });

        synchronized (featuresLock) {
            addTopFeature(results, peaks, "OSM_HIGHEST_PEAK", now);
            addTopFeature(results, volcanoes, "OSM_HIGHEST_VOLCANO", now);
            addTopFeature(results, waterfalls, "OSM_WATERFALL", now);

            if (!lakes.isEmpty()) {
                lakes.sort(Comparator.comparingDouble(NamedFeature::value).reversed());
                NamedFeature biggest = lakes.getFirst();
                if (biggest.value() > 1_000_000) {
                    results.add(OsmCountryStatistic.builder()
                            .countryCode(countryCode)
                            .indicatorCode("OSM_LARGEST_LAKE")
                            .category(Category.GEOGRAPHY)
                            .valueText(biggest.name())
                            .weight(BigDecimal.valueOf(0.7))
                            .fetchedAt(now)
                            .build());
                }
            }
        }

        return results;
    }

    private void addTopFeature(List<OsmCountryStatistic> results, List<NamedFeature> list,
                               String code, Instant now) {
        if (list.isEmpty()) return;

        list.sort(Comparator.comparingDouble(NamedFeature::value).reversed());
        NamedFeature top = list.getFirst();

        results.add(OsmCountryStatistic.builder()
                .countryCode(countryCode)
                .indicatorCode(code)
                .category(Category.GEOGRAPHY)
                .value(BigDecimal.valueOf(top.value()))
                .valueText(String.format("%s (%.0f m)", top.name(), top.value()))
                .weight(BigDecimal.ONE)
                .fetchedAt(now)
                .build());
    }

    public void logStatisticsSummary() {
        log.debug("[{}] ========== STATISTICS SUMMARY ==========", countryCode);

        for (OsmTagMapping mapping : OsmTagMapping.values()) {
            long count = counters.get(mapping).get();
            Map<String, OsmPatternInfo> accepted = acceptedPatterns.get(mapping);
            Map<String, OsmPatternInfo> rejected = rejectedPatterns.get(mapping);

            long totalRejected = rejected.values().stream().mapToLong(p -> p.count().get()).sum();

            if (count > 0 || totalRejected > 0) {
                log.debug("[{}] {} : {} accepted ({} patterns), {} rejected ({} patterns)", countryCode, mapping.getDbCode(), count, accepted.size(), totalRejected, rejected.size());

                if (!accepted.isEmpty()) {
                    log.debug("[{}]   ACCEPTED PATTERNS:", countryCode);
                    accepted.entrySet().stream()
                            .sorted((a, b) -> Long.compare(b.getValue().count().get(), a.getValue().count().get()))
                            .forEach(e -> log.debug("[{}]     [{}x] {} | example: {} [{}]", countryCode, e.getValue().count().get(), e.getKey(), e.getValue().exampleName(), e.getValue().exampleId()));
                }

                if (!rejected.isEmpty()) {
                    log.debug("[{}]   REJECTED PATTERNS:", countryCode);
                    rejected.entrySet().stream()
                            .sorted((a, b) -> Long.compare(b.getValue().count().get(), a.getValue().count().get()))
                            .forEach(e -> log.debug("[{}]     [{}x] {} | example: {} [{}]", countryCode, e.getValue().count().get(), e.getKey(), e.getValue().exampleName(), e.getValue().exampleId()));
                }
            }
        }
        log.debug("[{}] ========================================", countryCode);
    }
}

package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.mapillary.dto.MapillaryImageResult;
import io.clueterra.questions.clients.mapillary.dto.MapillaryStoredImage;
import io.clueterra.questions.clients.mapillary.dto.PanoramaCandidate;
import io.clueterra.questions.clients.mapillary.dto.TileCoord;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapillaryPanoramaFetcher {

    private static final String STORAGE_PREFIX = "mapillary";
    private static final String MAPILLARY_LICENSE_LABEL = "CC BY-SA 4.0";
    private static final String MAPILLARY_LICENSE_URL =
            "https://creativecommons.org/licenses/by-sa/4.0/";
    private static final String SEARCH_FIELDS = "id,geometry,is_pano,thumb_original_url,width,height,quality_score,captured_at,creator,sequence";
    private static final int BBOX_RESULT_LIMIT = 100;
    private static final int LANDMARK_RESULT_LIMIT = 50;
    private static final int LANDMARK_RADIUS_M = 50;
    private static final double LANDMARK_BBOX_RADIUS = 0.002;
    private static final double LANDMARK_FALLBACK_MAX_DIST_M = 250;
    private static final double BBOX_RADIUS = 0.005;
    private static final int CITY_TILE_RADIUS = 2;
    private static final int RURAL_TILE_RADIUS = 3;
    private static final int MAX_PANORAMAS_PER_COUNTRY = 36;
    private static final int MAX_LANDMARKS_PER_COUNTRY = 12;
    private static final int MAX_LANDMARK_SEEDS_PER_COUNTRY = 18;
    private static final int MAX_METADATA_FETCHES_PER_SCAN = 24;
    private static final int MAX_CITY_TILE_FALLBACK_SCANS = 3;
    private static final int MAX_RURAL_TILE_FALLBACK_SCANS = 2;
    private static final double LANDMARK_MIN_DIST_M = 500;
    private static final double CITY_MIN_DIST_M = 2000;
    private static final double RURAL_MIN_DIST_M = 10000;
    private static final long MIN_CAPTURED_AT_MS = Instant.parse("2015-01-01T00:00:00Z").toEpochMilli();

    private final HttpClientHelper httpClient;
    private final MapillaryResponseParser parser;
    private final MapillaryTileScanner tileScanner;
    private final Optional<ImageStorageService> imageStorageService;
    private final ObjectMapper objectMapper;

    public Map<String, MapillaryStoredImage> fetchPanoramas(
            String countryCode, List<double[]> landmarks, List<double[]> cities, List<double[]> ruralCities,
            String baseUrl, String apiKey, Duration timeout) {

        Map<String, MapillaryStoredImage> result = new LinkedHashMap<>();
        Set<Long> usedIds = new HashSet<>();
        Set<String> usedSequences = new HashSet<>();
        List<double[]> storedCoords = new ArrayList<>();

        int landmarkCount = 0;
        int cityCount = 0;
        int cityTileFallbackScans = 0;

        int landmarkSeedsScanned = 0;
        for (double[] landmark : landmarks) {
            if (landmarkCount >= MAX_LANDMARKS_PER_COUNTRY
                    || landmarkSeedsScanned >= MAX_LANDMARK_SEEDS_PER_COUNTRY) break;
            landmarkSeedsScanned++;
            landmarkCount = fetchLandmark(
                    landmark[0], landmark[1], usedIds, usedSequences, storedCoords,
                    baseUrl, apiKey, timeout, result, landmarkCount);
        }

        for (double[] city : cities) {
            if (result.size() >= MAX_PANORAMAS_PER_COUNTRY) break;

            double lat = city[0];
            double lon = city[1];

            int before = cityCount;
            cityCount = fetchBbox(
                    "MAPILLARY_CITY_PANO", lat, lon, BBOX_RADIUS,
                    usedIds, usedSequences, storedCoords, CITY_MIN_DIST_M,
                    Double.POSITIVE_INFINITY, baseUrl, apiKey, timeout, result, cityCount);
            if (cityCount == before && cityTileFallbackScans < MAX_CITY_TILE_FALLBACK_SCANS) {
                cityTileFallbackScans++;
                cityCount = fetchViaTiles(
                        "MAPILLARY_CITY_PANO", lat, lon, CITY_TILE_RADIUS,
                        usedIds, usedSequences, storedCoords, CITY_MIN_DIST_M,
                        baseUrl, apiKey, timeout, result, cityCount);
            }
        }

        int ruralCount = 0;
        int ruralTileFallbackScans = 0;
        for (double[] town : ruralCities) {
            if (result.size() >= MAX_PANORAMAS_PER_COUNTRY) break;

            int before = ruralCount;
            ruralCount = fetchBbox(
                    "MAPILLARY_RURAL_PANO", town[0], town[1], BBOX_RADIUS,
                    usedIds, usedSequences, storedCoords, RURAL_MIN_DIST_M,
                    Double.POSITIVE_INFINITY, baseUrl, apiKey, timeout, result, ruralCount);

            if (ruralCount == before && result.size() < MAX_PANORAMAS_PER_COUNTRY
                    && ruralTileFallbackScans < MAX_RURAL_TILE_FALLBACK_SCANS) {
                ruralTileFallbackScans++;
                ruralCount = fetchViaTiles(
                        "MAPILLARY_RURAL_PANO", town[0], town[1], RURAL_TILE_RADIUS,
                        usedIds, usedSequences, storedCoords, RURAL_MIN_DIST_M,
                        baseUrl, apiKey, timeout, result, ruralCount);
            }
        }

        log.info("[MAPILLARY] {}: {} landmark, {} city, {} rural (total {})",
                countryCode, landmarkCount, cityCount, ruralCount, result.size());

        return result;
    }

    private int fetchLandmark(
            double lat, double lon, Set<Long> usedIds, Set<String> usedSequences,
            List<double[]> storedCoords, String baseUrl, String apiKey, Duration timeout,
            Map<String, MapillaryStoredImage> result, int startCount) {
        String url = baseUrl + "/images"
                + "?access_token=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&fields=" + SEARCH_FIELDS
                + "&is_pano=true"
                + "&lat=" + lat
                + "&lng=" + lon
                + "&radius=" + LANDMARK_RADIUS_M
                + "&limit=" + LANDMARK_RESULT_LIMIT;

        try {
            Optional<String> response = httpClient.getOptional(DataSource.MAPILLARY, url, timeout);
            List<MapillaryImageResult> candidates = response.stream()
                    .flatMap(body -> parser.parse(body).stream())
                    .filter(img -> img.capturedAt() >= MIN_CAPTURED_AT_MS)
                    .filter(img -> haversineMeters(lat, lon, img.latitude(), img.longitude()) <= LANDMARK_RADIUS_M)
                    .sorted(Comparator.comparingDouble(MapillaryImageResult::qualityScore).reversed()
                            .thenComparing(Comparator.comparingLong(MapillaryImageResult::capturedAt).reversed()))
                    .toList();
            for (MapillaryImageResult img : candidates) {
                if (usedIds.contains(img.id()) || isUsedSequence(img.sequenceId(), usedSequences)
                        || isTooClose(img.latitude(), img.longitude(), storedCoords, LANDMARK_MIN_DIST_M)) {
                    continue;
                }
                Optional<MapillaryStoredImage> image = storeImage(
                        img.id(), img.imageUrl(), img.creatorUsername());
                if (image.isPresent()) {
                    usedIds.add(img.id());
                    rememberSequence(img.sequenceId(), usedSequences);
                    storedCoords.add(new double[]{img.latitude(), img.longitude()});
                    result.put("MAPILLARY_LANDMARK_PANO_" + (startCount + 1), image.get());
                    return startCount + 1;
                }
            }
        } catch (Exception e) {
            log.warn("[MAPILLARY] Landmark search failed at ({}, {})", lat, lon, e);
        }
        return fetchBbox(
                "MAPILLARY_LANDMARK_PANO", lat, lon, LANDMARK_BBOX_RADIUS,
                usedIds, usedSequences, storedCoords, LANDMARK_MIN_DIST_M,
                LANDMARK_FALLBACK_MAX_DIST_M, baseUrl, apiKey, timeout, result, startCount);
    }

    private int fetchBbox(
            String tierCode, double lat, double lon, double radius,
            Set<Long> usedIds, Set<String> usedSequences, List<double[]> storedCoords, double minDistM,
            double maxSeedDistanceM,
            String baseUrl, String apiKey, Duration timeout,
            Map<String, MapillaryStoredImage> result, int startCount) {

        String bbox = String.format(
                Locale.US,
                "%.6f,%.6f,%.6f,%.6f",
                lon - radius, lat - radius,
                lon + radius, lat + radius);

        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = baseUrl + "/images"
                + "?access_token=" + encodedKey
                + "&fields=" + SEARCH_FIELDS
                + "&is_pano=true"
                + "&bbox=" + bbox
                + "&limit=" + BBOX_RESULT_LIMIT;

        try {
            Optional<String> response = httpClient.getOptional(DataSource.MAPILLARY, url, timeout);
            if (response.isEmpty()) {
                return startCount;
            }

            List<MapillaryImageResult> candidates = parser.parse(response.get()).stream()
                    .filter(img -> !usedIds.contains(img.id()))
                    .filter(img -> img.capturedAt() >= MIN_CAPTURED_AT_MS)
                    .filter(img -> haversineMeters(lat, lon, img.latitude(), img.longitude()) <= maxSeedDistanceM)
                    .sorted(Double.isFinite(maxSeedDistanceM)
                            ? Comparator.comparingDouble(img -> haversineMeters(
                                    lat, lon, img.latitude(), img.longitude()))
                            : Comparator.comparingDouble(MapillaryImageResult::qualityScore).reversed()
                                    .thenComparing(Comparator.comparingLong(MapillaryImageResult::capturedAt).reversed()))
                    .toList();

            int stored = startCount;
            for (MapillaryImageResult img : candidates) {
                if (result.size() >= MAX_PANORAMAS_PER_COUNTRY) break;
                if (isUsedSequence(img.sequenceId(), usedSequences)
                        || isTooClose(img.latitude(), img.longitude(), storedCoords, minDistM)) continue;

                Optional<MapillaryStoredImage> image = storeImage(
                        img.id(), img.imageUrl(), img.creatorUsername());
                if (image.isPresent()) {
                    usedIds.add(img.id());
                    rememberSequence(img.sequenceId(), usedSequences);
                    stored++;
                    String code = tierCode + "_" + stored;
                    result.put(code, image.get());
                    storedCoords.add(new double[]{img.latitude(), img.longitude()});
                    log.debug("[MAPILLARY] Found {} via bbox imageId={}", code, img.id());
                    break;
                }
            }
            return stored;
        } catch (Exception e) {
            log.warn("[MAPILLARY] Bbox search failed for {}", tierCode, e);
            return startCount;
        }
    }

    private int fetchViaTiles(
            String tierCode, double lat, double lon, int gridRadius,
            Set<Long> usedIds, Set<String> usedSequences, List<double[]> storedCoords, double minDistM,
            String baseUrl, String apiKey, Duration timeout,
            Map<String, MapillaryStoredImage> result, int startCount) {

        Map<TileCoord, List<Long>> idsByTile =
                tileScanner.findPanoramaIdsByTile(lat, lon, gridRadius, apiKey, usedIds);

        List<TileCoord> tileOrder = new ArrayList<>(idsByTile.keySet());
        Map<TileCoord, Integer> tilePointers = new HashMap<>();
        for (TileCoord tile : tileOrder) {
            tilePointers.put(tile, 0);
        }

        int stored = startCount;
        int metadataFetches = 0;

        while (result.size() < MAX_PANORAMAS_PER_COUNTRY
                && metadataFetches < MAX_METADATA_FETCHES_PER_SCAN
                && !tileOrder.isEmpty()) {

            List<TileCoord> exhausted = new ArrayList<>();

            for (int i = 0; i < tileOrder.size()
                    && result.size() < MAX_PANORAMAS_PER_COUNTRY
                    && metadataFetches < MAX_METADATA_FETCHES_PER_SCAN; i++) {

                TileCoord tile = tileOrder.get(i);
                List<Long> ids = idsByTile.get(tile);
                int ptr = tilePointers.get(tile);
                boolean foundInTile = false;

                while (ptr < ids.size() && metadataFetches < MAX_METADATA_FETCHES_PER_SCAN) {
                    Long id = ids.get(ptr);
                    ptr++;

                    if (usedIds.contains(id)) continue;

                    metadataFetches++;
                    Optional<PanoramaCandidate> candidate =
                            fetchImageMetadata(id, tile, baseUrl, apiKey, timeout);

                    if (candidate.isEmpty()) continue;

                    PanoramaCandidate c = candidate.get();
                    if (isUsedSequence(c.sequenceId(), usedSequences)
                            || isTooClose(c.lat(), c.lon(), storedCoords, minDistM)) continue;

                    Optional<MapillaryStoredImage> image = storeImage(
                            c.id(), c.imageUrl(), c.creatorUsername());
                    if (image.isPresent()) {
                        usedIds.add(c.id());
                        rememberSequence(c.sequenceId(), usedSequences);
                        stored++;
                        String code = tierCode + "_" + stored;
                        result.put(code, image.get());
                        storedCoords.add(new double[]{c.lat(), c.lon()});
                        log.debug("[MAPILLARY] Found {} via tiles imageId={} tile=({},{})",
                                code, c.id(), tile.x(), tile.y());
                        foundInTile = true;
                        break;
                    }
                }

                tilePointers.put(tile, ptr);
                if (!foundInTile) {
                    exhausted.add(tile);
                } else {
                    return stored;
                }
            }

            tileOrder.removeAll(exhausted);
        }

        return stored;
    }

    private Optional<PanoramaCandidate> fetchImageMetadata(
            Long imageId, TileCoord tile, String baseUrl, String apiKey, Duration timeout) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = baseUrl + "/" + imageId
                + "?fields=id,is_pano,thumb_original_url,width,height,quality_score,captured_at,geometry,creator,sequence"
                + "&access_token=" + encodedKey;

        try {
            String response = httpClient.get(DataSource.MAPILLARY, url, timeout);
            JsonNode node = objectMapper.readTree(response);
            Optional<String> originalUrl = MapillaryPanoramaQuality.usableOriginalUrl(node);
            long capturedAt = node.path("captured_at").asLong(0);

            if (originalUrl.isEmpty() || capturedAt < MIN_CAPTURED_AT_MS) {
                return Optional.empty();
            }

            double imgLat = 0;
            double imgLon = 0;
            JsonNode coords = node.path("geometry").path("coordinates");
            if (coords.isArray() && coords.size() >= 2) {
                imgLon = coords.get(0).asDouble();
                imgLat = coords.get(1).asDouble();
            }

            String creatorUsername = node.path("creator").path("username").asText(null);
            return Optional.of(new PanoramaCandidate(
                    imageId, originalUrl.get(), capturedAt, tile, imgLat, imgLon, creatorUsername,
                    node.path("sequence").asText(null)));
        } catch (Exception e) {
            log.debug("[MAPILLARY] Failed to fetch metadata for imageId={}: {}", imageId, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isTooClose(double lat, double lon, List<double[]> storedCoords, double minDistM) {
        for (double[] coord : storedCoords) {
            if (haversineMeters(lat, lon, coord[0], coord[1]) < minDistM) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsedSequence(String sequenceId, Set<String> usedSequences) {
        return sequenceId != null && !sequenceId.isBlank() && usedSequences.contains(sequenceId);
    }

    private static void rememberSequence(String sequenceId, Set<String> usedSequences) {
        if (sequenceId != null && !sequenceId.isBlank()) {
            usedSequences.add(sequenceId);
        }
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Optional<MapillaryStoredImage> storeImage(
            long imageId, String imageUrl, String creatorUsername) {
        String sourcePageUrl = mapillarySourcePageUrl(imageId);
        String attribution = creatorUsername == null || creatorUsername.isBlank()
                ? "Street-level imagery via Mapillary."
                : "Mapillary image by @" + creatorUsername + ".";
        if (imageStorageService.isEmpty()) {
            return Optional.of(new MapillaryStoredImage(
                    imageUrl,
                    imageUrl,
                    sourcePageUrl,
                    creatorUsername,
                    MAPILLARY_LICENSE_LABEL,
                    MAPILLARY_LICENSE_URL,
                    attribution));
        }
        return imageStorageService.flatMap(service -> service.storeDirect(imageUrl, STORAGE_PREFIX))
                .map(storedUrl -> new MapillaryStoredImage(
                        storedUrl,
                        imageUrl,
                        sourcePageUrl,
                        creatorUsername,
                        MAPILLARY_LICENSE_LABEL,
                        MAPILLARY_LICENSE_URL,
                        attribution));
    }

    private String mapillarySourcePageUrl(long imageId) {
        return "https://www.mapillary.com/app/?pKey=" + imageId + "&focus=photo";
    }

}

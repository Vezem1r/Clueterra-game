package io.clueterra.questions.clients.mapillary;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.clueterra.questions.clients.mapillary.dto.TileCoord;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class MapillaryTileScanner {

    private static final String TILE_URL =
            "https://tiles.mapillary.com/maps/vtp/mly1_public/2/%d/%d/%d?access_token=%s";
    private static final int ZOOM = 14;
    private static final int MAX_IDS_PER_SCAN = 200;

    private final HttpClient tileClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Map<TileCoord, List<Long>> findPanoramaIdsByTile(
            double lat, double lon, int gridRadius, String apiKey, Set<Long> excludeIds) {

        int cx = lonToTile(lon);
        int cy = latToTile(lat);

        List<int[]> positions = buildSpiralPositions(gridRadius);
        Map<TileCoord, List<Long>> grouped = new HashMap<>();
        Set<Long> seen = new HashSet<>(excludeIds);
        int totalCount = 0;

        for (int[] pos : positions) {
            int tileX = cx + pos[0];
            int tileY = cy + pos[1];
            List<Long> ids = fetchTilePanoIds(tileX, tileY, apiKey);

            for (Long id : ids) {
                if (totalCount >= MAX_IDS_PER_SCAN) {
                    break;
                }
                if (!seen.contains(id)) {
                    seen.add(id);
                    TileCoord coord = new TileCoord(tileX, tileY);
                    grouped.computeIfAbsent(coord, k -> new ArrayList<>()).add(id);
                    totalCount++;
                }
            }
            if (totalCount >= MAX_IDS_PER_SCAN) {
                break;
            }
        }

        log.debug(
                "[MAPILLARY] Tile scan at ({},{}) radius={}: found {} panoramas across {} tiles",
                lat, lon, gridRadius, totalCount, grouped.size());
        return grouped;
    }

    private List<Long> fetchTilePanoIds(int x, int y, String apiKey) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = String.format(TILE_URL, ZOOM, x, y, encodedKey);

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();

            HttpResponse<byte[]> response =
                    tileClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200 || response.body().length == 0) {
                return List.of();
            }

            return parseTile(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[MAPILLARY] Tile {}/{}/{} interrupted", ZOOM, x, y);
            return List.of();
        } catch (Exception e) {
            log.debug("[MAPILLARY] Tile {}/{}/{} failed: {}", ZOOM, x, y, e.getMessage());
            return List.of();
        }
    }

    List<Long> parseTile(byte[] data) throws IOException {
        List<Long> allIds = new ArrayList<>();
        CodedInputStream cis = CodedInputStream.newInstance(data);

        while (!cis.isAtEnd()) {
            int tag = cis.readTag();
            int field = WireFormat.getTagFieldNumber(tag);

            if (field == 3) {
                byte[] layerBytes = cis.readByteArray();
                allIds.addAll(parseLayer(layerBytes));
            } else {
                cis.skipField(tag);
            }
        }
        return allIds;
    }

    private List<Long> parseLayer(byte[] data) throws IOException {
        CodedInputStream cis = CodedInputStream.newInstance(data);
        String name = null;
        List<String> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<byte[]> featureBlobs = new ArrayList<>();

        while (!cis.isAtEnd()) {
            int tag = cis.readTag();
            int field = WireFormat.getTagFieldNumber(tag);

            switch (field) {
                case 1 -> name = cis.readString();
                case 2 -> featureBlobs.add(cis.readByteArray());
                case 3 -> keys.add(cis.readString());
                case 4 -> values.add(parseValue(cis.readByteArray()));
                default -> cis.skipField(tag);
            }
        }

        if (!"image".equals(name)) {
            return List.of();
        }

        int idKey = keys.indexOf("id");
        int panoKey = keys.indexOf("is_pano");
        if (idKey < 0 || panoKey < 0) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        for (byte[] fb : featureBlobs) {
            extractPanoId(fb, idKey, panoKey, values, ids);
        }
        return ids;
    }

    private void extractPanoId(
            byte[] data, int idKey, int panoKey, List<Object> values, List<Long> result)
            throws IOException {
        List<Integer> tags = readFeatureTags(data);

        Long id = null;
        Boolean isPano = null;

        for (int i = 0; i + 1 < tags.size(); i += 2) {
            int keyIdx = tags.get(i);
            int valIdx = tags.get(i + 1);
            if (valIdx >= values.size()) {
                continue;
            }

            if (keyIdx == idKey) {
                Object val = values.get(valIdx);
                if (val instanceof Number n) {
                    id = n.longValue();
                }
            }
            if (keyIdx == panoKey) {
                Object val = values.get(valIdx);
                if (val instanceof Boolean b) {
                    isPano = b;
                }
            }
        }

        if (Boolean.TRUE.equals(isPano) && id != null) {
            result.add(id);
        }
    }

    private List<Integer> readFeatureTags(byte[] data) throws IOException {
        CodedInputStream cis = CodedInputStream.newInstance(data);
        List<Integer> tags = new ArrayList<>();

        while (!cis.isAtEnd()) {
            int tag = cis.readTag();
            int field = WireFormat.getTagFieldNumber(tag);

            if (field == 2) {
                int length = cis.readRawVarint32();
                int limit = cis.pushLimit(length);
                while (cis.getBytesUntilLimit() > 0) {
                    tags.add(cis.readUInt32());
                }
                cis.popLimit(limit);
            } else {
                cis.skipField(tag);
            }
        }
        return tags;
    }

    private Object parseValue(byte[] data) throws IOException {
        CodedInputStream cis = CodedInputStream.newInstance(data);
        while (!cis.isAtEnd()) {
            int tag = cis.readTag();
            int field = WireFormat.getTagFieldNumber(tag);

            switch (field) {
                case 1 -> {
                    return cis.readString();
                }
                case 2 -> {
                    return cis.readFloat();
                }
                case 3 -> {
                    return cis.readDouble();
                }
                case 4 -> {
                    return cis.readInt64();
                }
                case 5 -> {
                    return cis.readUInt64();
                }
                case 6 -> {
                    return cis.readSInt64();
                }
                case 7 -> {
                    return cis.readBool();
                }
                default -> cis.skipField(tag);
            }
        }
        return null;
    }

    private static List<int[]> buildSpiralPositions(int radius) {
        List<int[]> positions = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                positions.add(new int[]{dx, dy});
            }
        }
        positions.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1]));
        return positions;
    }

    private static int lonToTile(double lon) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << ZOOM));
    }

    private static int latToTile(double lat) {
        double latRad = Math.toRadians(lat);
        return (int)
                Math.floor(
                        (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI)
                                / 2.0
                                * (1 << ZOOM));
    }
}

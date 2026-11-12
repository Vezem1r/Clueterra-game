package io.clueterra.questions.clients.citybikes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkDetail;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkIndexEntry;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CityBikesParser {

    private final ObjectMapper objectMapper;

    public List<CityBikesNetworkIndexEntry> parseNetworkIndex(String body) {
        try {
            JsonNode networks = objectMapper.readTree(body).path("networks");
            if (!networks.isArray()) {
                return List.of();
            }

            List<CityBikesNetworkIndexEntry> result = new ArrayList<>();
            for (JsonNode node : networks) {
                parseNetworkIndexEntry(node).ifPresent(result::add);
            }
            return result;
        } catch (Exception e) {
            log.debug("[CityBikes] Failed to parse network index: {}", e.getMessage());
            return List.of();
        }
    }

    public Optional<CityBikesNetworkDetail> parseNetworkDetail(String body, CityBikesNetworkSummary summary) {
        try {
            JsonNode network = objectMapper.readTree(body).path("network");
            JsonNode stations = network.path("stations");
            if (!stations.isArray()) {
                return Optional.empty();
            }

            int stationCount = 0;
            int freeBikes = 0;
            int emptySlots = 0;
            int ebikes = 0;
            for (JsonNode station : stations) {
                stationCount++;
                freeBikes += station.path("free_bikes").asInt(0);
                emptySlots += station.path("empty_slots").asInt(0);
                ebikes += station.path("extra").path("ebikes").asInt(0);
            }

            String name = network.path("name").asText(summary.name());
            JsonNode location = network.path("location");
            String city = location.path("city").asText(summary.city());
            String company = companyText(network.path("company"));
            if (!StringUtils.hasText(company)) {
                company = summary.company();
            }

            return Optional.of(new CityBikesNetworkDetail(
                    summary.id(),
                    name,
                    city,
                    summary.iso2(),
                    company,
                    stationCount,
                    freeBikes,
                    emptySlots,
                    ebikes
            ));
        } catch (Exception e) {
            log.debug("[CityBikes] Failed to parse detail for {}: {}", summary.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CityBikesNetworkIndexEntry> parseNetworkIndexEntry(JsonNode node) {
        String id = node.path("id").asText(null);
        String name = node.path("name").asText(null);
        JsonNode location = node.path("location");
        String countryCode = location.path("country").asText(null);
        if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || !StringUtils.hasText(countryCode)) {
            return Optional.empty();
        }

        return Optional.of(new CityBikesNetworkIndexEntry(
                id,
                name,
                location.path("city").asText(null),
                countryCode,
                node.path("href").asText(null),
                companyText(node.path("company"))
        ));
    }

    private static String companyText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> companies = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText(null);
                if (StringUtils.hasText(value)) {
                    companies.add(value);
                }
            }
            return companies.stream().limit(2).collect(Collectors.joining(" / "));
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }
}

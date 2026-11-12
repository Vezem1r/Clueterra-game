package io.clueterra.questions.clients.citybikes.dto;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record CityBikesNetworkDetail(
        String id,
        String name,
        String city,
        String iso2,
        String company,
        int stationCount,
        int freeBikes,
        int emptySlots,
        int ebikes
) {
    public String subject() {
        if (StringUtils.hasText(city)
                && !name.toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
            return name + " in " + city;
        }
        return name;
    }
}

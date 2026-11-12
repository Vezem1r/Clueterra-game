package io.clueterra.questions.clients.citybikes.dto;

public record CityBikesNetworkIndexEntry(
        String id,
        String name,
        String city,
        String countryCode,
        String href,
        String company
) {
}

package io.clueterra.questions.clients.citybikes.dto;

public record CityBikesNetworkSummary(
        String id,
        String name,
        String city,
        String iso2,
        String href,
        String company
) {
}

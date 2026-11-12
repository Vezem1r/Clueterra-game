package io.clueterra.api.projectpages.service;

import java.util.Arrays;
import java.util.Optional;

public enum ProjectHubCategory {
    METRO_RAIL("railways-and-metros"),
    ROAD_HIGHWAY("roads-and-highways"),
    BRIDGE("bridges"),
    ENERGY("energy-projects"),
    SKYSCRAPER("skyscrapers"),
    STADIUM("stadiums"),
    AIRPORT("airports"),
    SPACE("space-missions"),
    TUNNEL("tunnels"),
    DAM("dams"),
    PORT("ports");

    private final String slug;

    ProjectHubCategory(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Optional<ProjectHubCategory> fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(category -> category.slug.equals(slug))
                .findFirst();
    }

    public static Optional<ProjectHubCategory> fromCode(String code) {
        return Arrays.stream(values())
                .filter(category -> category.name().equals(code))
                .findFirst();
    }
}

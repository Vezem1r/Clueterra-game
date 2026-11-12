package io.clueterra.questions.entity.enums;

import lombok.Getter;

@Getter
public enum HintType {
    RANKING("Ranking hint", "5th largest economy"),
    COMPARISON("Comparison hint", "GDP higher than France"),
    PERCENTILE("Percentile hint", "Top 10% by population"),
    ABSOLUTE_FACT("Absolute fact", "Population: 83 million"),
    RELATIVE_FACT("Relative fact", "30% of Amazon rainforest"),
    SUPERLATIVE("Superlative hint", "Longest coastline in Africa"),
    NEIGHBOR("Neighbor hint", "Borders 9 countries"),
    CULTURAL("Cultural hint", "Official language: Portuguese"),
    HISTORICAL("Historical hint", "Independent since 1947"),
    UNIQUE_FEATURE("Unique feature", "Only country with non-rectangular flag"),
    IMAGE_FACT("Image fact", "Photo of a native animal"),
    PANORAMA("Panorama hint", "360° street-level view from this country");

    private final String displayName;
    private final String example;

    HintType(String displayName, String example) {
        this.displayName = displayName;
        this.example = example;
    }
}

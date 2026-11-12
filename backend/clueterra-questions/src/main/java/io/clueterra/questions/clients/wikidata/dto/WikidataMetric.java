package io.clueterra.questions.clients.wikidata.dto;

import io.clueterra.questions.entity.enums.Category;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WikidataMetric {
    NATIONAL_ANTHEM("WIKIDATA_NATIONAL_ANTHEM", "National anthem", Category.CULTURE,
            "P85", MetricType.ITEM_LABEL_SET),
    CALLING_CODE("WIKIDATA_CALLING_CODE", "International calling code", Category.INFRASTRUCTURE,
            "P474", MetricType.STRING_SET),
    TOP_LEVEL_DOMAIN("WIKIDATA_TOP_LEVEL_DOMAIN", "Country-code top-level domain", Category.INFRASTRUCTURE,
            "P78", MetricType.ITEM_LABEL_SET),
    DRIVING_SIDE("WIKIDATA_DRIVING_SIDE", "Driving side", Category.INFRASTRUCTURE,
            "P1622", MetricType.ITEM_LABEL_SET),
    ELECTRICAL_PLUG("WIKIDATA_ELECTRICAL_PLUG", "Electrical plug types", Category.INFRASTRUCTURE,
            "P2853", MetricType.ITEM_LABEL_SET);

    private final String code;
    private final String label;
    private final Category category;
    private final String propertyId;
    private final MetricType type;
}

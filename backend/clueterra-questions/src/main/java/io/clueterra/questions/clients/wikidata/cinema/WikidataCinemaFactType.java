package io.clueterra.questions.clients.wikidata.cinema;

public enum WikidataCinemaFactType {
    ACTOR_WORKS("Q10800557", "P161", "A", "Actor born in this country", "Film featuring"),
    DIRECTOR_WORKS("Q2526255", "P57", "D", "Director born in this country", "Film directed by");

    private final String occupationItemId;
    private final String workRelationProperty;
    private final String statisticRoleCode;
    private final String personIndicatorName;
    private final String workIndicatorNamePrefix;

    WikidataCinemaFactType(String occupationItemId,
                           String workRelationProperty,
                           String statisticRoleCode,
                           String personIndicatorName,
                           String workIndicatorNamePrefix) {
        this.occupationItemId = occupationItemId;
        this.workRelationProperty = workRelationProperty;
        this.statisticRoleCode = statisticRoleCode;
        this.personIndicatorName = personIndicatorName;
        this.workIndicatorNamePrefix = workIndicatorNamePrefix;
    }

    public String occupationItemId() {
        return occupationItemId;
    }

    public String workRelationProperty() {
        return workRelationProperty;
    }

    public String statisticRoleCode() {
        return statisticRoleCode;
    }

    public String personIndicatorName() {
        return personIndicatorName;
    }

    public String workIndicatorName(String subject) {
        return workIndicatorNamePrefix + " " + subject;
    }

    public static WikidataCinemaFactType fromStatisticRoleCode(String roleCode) {
        for (WikidataCinemaFactType value : values()) {
            if (value.statisticRoleCode.equals(roleCode)) {
                return value;
            }
        }
        return null;
    }

    public static WikidataCinemaFactType fromOccupationItemId(String itemId) {
        for (WikidataCinemaFactType value : values()) {
            if (value.occupationItemId.equals(itemId)) {
                return value;
            }
        }
        return null;
    }
}

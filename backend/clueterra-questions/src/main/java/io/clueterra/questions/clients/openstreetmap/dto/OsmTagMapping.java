package io.clueterra.questions.clients.openstreetmap.dto;

import io.clueterra.questions.entity.enums.Category;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Getter
@RequiredArgsConstructor
public enum OsmTagMapping {
    SADDLE("natural", "saddle", "OSM_SADDLE", Category.GEOGRAPHY, 0.3, true, tags -> true),
    GEYSER("natural", "geyser", "OSM_GEYSER", Category.GEOGRAPHY, 0.9, true, tags -> true),
    GLACIER("natural", "glacier", "OSM_GLACIER", Category.GEOGRAPHY, 0.8, true, tags -> true),
    WATERFALL("waterway", "waterfall", "OSM_WATERFALL", Category.GEOGRAPHY, 0.5, true, tags -> true),
    WINDMILL("man_made", "windmill", "OSM_WINDMILL", Category.INFRASTRUCTURE, 0.8, true, tags -> true),
    DAM("waterway", "dam", "OSM_DAM", Category.INFRASTRUCTURE, 0.5, true, tags -> true),
    LOCK_GATE("waterway", "lock_gate", "OSM_LOCK", Category.INFRASTRUCTURE, 0.5, true, tags -> true),
    WATER_TOWER("man_made", "water_tower", "OSM_WATER_TOWER", Category.INFRASTRUCTURE, 0.3, true, tags -> true),
    FERRY_TERMINAL("amenity", "ferry_terminal", "OSM_FERRY", Category.INFRASTRUCTURE, 0.5, true, tags -> true),
    CABLE_CAR("aerialway", "station", "OSM_CABLE_CAR", Category.INFRASTRUCTURE, 0.6, true, tags -> true),
    CASTLE("historic", "castle", "OSM_CASTLE", Category.CULTURE, 0.6, true, tags -> true),
    FORT("historic", "fort", "OSM_FORT", Category.CULTURE, 0.5, true, tags -> true),
    ARCHAEOLOGICAL("historic", "archaeological_site", "OSM_ARCHAEOLOGICAL", Category.CULTURE, 0.5, true, tags -> true),
    MONUMENT("historic", "monument", "OSM_MONUMENT", Category.CULTURE, 0.4, true, tags -> true),
    STADIUM("leisure", "stadium", "OSM_STADIUM", Category.CULTURE, 0.4, true, tags -> true),
    ICE_RINK("leisure", "ice_rink", "OSM_ICE_RINK", Category.CULTURE, 0.5, true, tags -> true),
    SKI_RESORT("landuse", "winter_sports", "OSM_SKI_RESORT", Category.CULTURE, 0.5, true, tags -> true),
    WATER_PARK("leisure", "water_park", "OSM_WATER_PARK", Category.CULTURE, 0.4, true, tags -> true),

    HOT_SPRING("natural", "hot_spring", "OSM_HOT_SPRING", Category.GEOGRAPHY, 0.8, true, tags -> {
        String manMade = tags.get("man_made");
        return !"swimming_pool".equals(manMade) && !"hot_tub".equals(manMade);
    }),

    VOLCANO("natural", "volcano", "OSM_VOLCANO", Category.GEOGRAPHY, 0.9, true, tags -> {
        String volcanoStatus = tags.get("volcano:status");
        if ("extinct".equals(volcanoStatus) || "dormant".equals(volcanoStatus)) {
            String name = tags.get("name");
            return name != null || tags.get("name:en") != null;
        }
        return true;
    }),

    PEAK("natural", "peak", "OSM_PEAK", Category.GEOGRAPHY, 0.2, true, tags -> {
        String access = tags.get("access");
        return !"no".equals(access) && !"private".equals(access);
    }),

    CAVE("natural", "cave_entrance", "OSM_CAVE", Category.GEOGRAPHY, 0.4, true, tags -> {
        String access = tags.get("access");
        return !"private".equals(access);
    }),

    BEACH("natural", "beach", "OSM_BEACH", Category.GEOGRAPHY, 0.3, true, tags -> {
        String leisure = tags.get("leisure");
        return !"swimming_pool".equals(leisure);
    }),

    NUCLEAR("generator:source", "nuclear", "OSM_NUCLEAR", Category.ECONOMY, 0.9, true, tags -> {
        String power = tags.get("power");
        return "plant".equals(power) || "generator".equals(power);
    }),

    WIND("generator:source", "wind", "OSM_WIND", Category.ECONOMY, 0.4, true, tags -> {
        String power = tags.get("power");
        return "plant".equals(power) || "generator".equals(power);
    }),

    SOLAR("generator:source", "solar", "OSM_SOLAR", Category.ECONOMY, 0.3, true, tags -> {
        String power = tags.get("power");
        return "plant".equals(power) || "generator".equals(power);
    }),

    COAL_PLANT("generator:source", "coal", "OSM_COAL_PLANT", Category.ECONOMY, 0.6, true, tags -> {
        String power = tags.get("power");
        return "plant".equals(power) || "generator".equals(power);
    }),

    HYDRO("generator:source", "hydro", "OSM_HYDRO", Category.ECONOMY, 0.5, true, tags -> {
        String power = tags.get("power");
        return "plant".equals(power) || "generator".equals(power);
    }),

    LIGHTHOUSE("man_made", "lighthouse", "OSM_LIGHTHOUSE", Category.INFRASTRUCTURE, 0.6, true, tags -> {
        String seamark = tags.get("seamark:type");
        String admiralty = tags.get("admiralty:number");
        String intRef = tags.get("int_ref");
        String ref = tags.get("ref");
        if (seamark != null || admiralty != null || intRef != null) {
            return true;
        }
        if (ref != null && tags.get("name") != null) {
            return true;
        }
        String tourism = tags.get("tourism");
        String historic = tags.get("historic");
        if ("attraction".equals(tourism) || historic != null) {
            return false;
        }
        return tags.get("name") != null;
    }),

    PIER("man_made", "pier", "OSM_PIER", Category.INFRASTRUCTURE, 0.3, true, tags -> {
        String access = tags.get("access");
        return !"private".equals(access) && !"no".equals(access);
    }),

    HELIPAD("aeroway", "helipad", "OSM_HELIPAD", Category.INFRASTRUCTURE, 0.4, true, tags -> {
        String access = tags.get("access");
        if ("private".equals(access)) {
            return false;
        }
        String usage = tags.get("usage");
        return !"private".equals(usage);
    }),

    AERODROME("aeroway", "aerodrome", "OSM_AIRPORT", Category.INFRASTRUCTURE, 0.5, true, tags -> {
        String type = tags.get("type");
        String aerodrome = tags.get("aerodrome");
        String access = tags.get("access");
        if ("military".equals(type) || "military".equals(aerodrome)) {
            return false;
        }
        if ("private".equals(aerodrome) || "airfield".equals(aerodrome)) {
            return false;
        }
        if ("private".equals(access)) {
            return false;
        }
        String icao = tags.get("icao");
        String iata = tags.get("iata");
        if (icao != null || iata != null) {
            return true;
        }
        return "international".equals(aerodrome) || "regional".equals(aerodrome) || "public".equals(aerodrome);
    }),

    RAILWAY_STATION("railway", "station", "OSM_RAILWAY_STATION", Category.INFRASTRUCTURE, 0.4, true, tags -> {
        String station = tags.get("station");
        return !"subway".equals(station) && !"light_rail".equals(station);
    }),

    MOSQUE("amenity", "place_of_worship", "OSM_MOSQUE", Category.CULTURE, 0.6, true, tags -> {
        String religion = tags.get("religion");
        return "muslim".equalsIgnoreCase(religion) || "islam".equalsIgnoreCase(religion);
    }),

    SYNAGOGUE("amenity", "place_of_worship", "OSM_SYNAGOGUE", Category.CULTURE, 0.8, true, tags -> {
        String religion = tags.get("religion");
        return "jewish".equalsIgnoreCase(religion) || "judaism".equalsIgnoreCase(religion);
    }),

    HINDU("amenity", "place_of_worship", "OSM_HINDU", Category.CULTURE, 0.9, true, tags -> {
        String religion = tags.get("religion");
        return "hindu".equalsIgnoreCase(religion) || "hinduism".equalsIgnoreCase(religion);
    }),

    BUDDHIST("amenity", "place_of_worship", "OSM_BUDDHIST", Category.CULTURE, 0.9, true, tags -> {
        String religion = tags.get("religion");
        return "buddhist".equalsIgnoreCase(religion) || "buddhism".equalsIgnoreCase(religion);
    }),

    SIKH("amenity", "place_of_worship", "OSM_SIKH", Category.CULTURE, 0.8, true, tags -> {
        String religion = tags.get("religion");
        return "sikh".equalsIgnoreCase(religion) || "sikhism".equalsIgnoreCase(religion);
    }),

    RUINS("historic", "ruins", "OSM_RUINS", Category.CULTURE, 0.4, true, tags -> {
        String building = tags.get("building");
        if ("house".equals(building) || "residential".equals(building) || "industrial".equals(building)) {
            return false;
        }
        String ruinsType = tags.get("ruins");
        return !"no".equals(ruinsType);
    }),

    MUSEUM("tourism", "museum", "OSM_MUSEUM", Category.CULTURE, 0.4, true, tags -> {
        String shop = tags.get("shop");
        return shop == null;
    }),

    ZOO("tourism", "zoo", "OSM_ZOO", Category.CULTURE, 0.4, true, tags -> {
        String shop = tags.get("shop");
        String amenity = tags.get("amenity");
        String zoo = tags.get("zoo");
        if ("pet".equals(shop) || "pet_grooming".equals(shop)) {
            return false;
        }
        if ("veterinary".equals(amenity)) {
            return false;
        }
        if ("petting_zoo".equals(zoo) || "enclosure".equals(zoo) || "aviary".equals(zoo) || "false".equals(zoo)) {
            return false;
        }
        String attraction = tags.get("attraction");
        return !"animal".equals(attraction) && !"enclosure".equals(attraction);
    }),

    THEME_PARK("tourism", "theme_park", "OSM_THEME_PARK", Category.CULTURE, 0.5, true, tags -> {
        String leisure = tags.get("leisure");
        if ("playground".equals(leisure)) {
            return false;
        }
        String sport = tags.get("sport");
        if ("climbing".equals(sport) || "climbing_adventure".equals(sport)) {
            return false;
        }
        String name = tags.get("name");
        if (name != null && name.toLowerCase().contains("lanov")) {
            return false;
        }
        return !"miniature_golf".equals(leisure);
    }),

    AQUARIUM("tourism", "aquarium", "OSM_AQUARIUM", Category.CULTURE, 0.5, true, tags -> {
        String shop = tags.get("shop");
        return !"pet".equals(shop);
    }),

    VIEWPOINT("tourism", "viewpoint", "OSM_VIEWPOINT", Category.CULTURE, 0.2, true, tags -> {
        String access = tags.get("access");
        return !"private".equals(access) && !"no".equals(access);
    }),

    GOLF_COURSE("leisure", "golf_course", "OSM_GOLF", Category.CULTURE, 0.3, true, tags -> {
        String golf = tags.get("golf");
        return !"driving_range".equals(golf) && !"practice".equals(golf);
    }),

    EMBASSY("office", "diplomatic", "OSM_EMBASSY", Category.POLITICS, 0.8, true, tags -> {
        String diplomatic = tags.get("diplomatic");
        return diplomatic == null || "embassy".equals(diplomatic) || "consulate".equals(diplomatic);
    }),

    PRISON("amenity", "prison", "OSM_PRISON", Category.POLITICS, 0.5, true, tags -> {
        String historic = tags.get("historic");
        return historic == null;
    }),

    COURTHOUSE("amenity", "courthouse", "OSM_COURTHOUSE", Category.POLITICS, 0.4, true, tags -> {
        String historic = tags.get("historic");
        return historic == null;
    }),

    BORDER_CONTROL("barrier", "border_control", "OSM_BORDER", Category.POLITICS, 0.4, true, tags -> true);

    private final String osmKey;
    private final String osmValue;
    private final String dbCode;
    private final Category category;
    private final double weight;
    private final boolean exactMatch;
    private final Predicate<Map<String, String>> validator;

    private static final Set<String> POWER_PLANT_GROUPING = Set.of("OSM_NUCLEAR", "OSM_WIND", "OSM_SOLAR", "OSM_COAL_PLANT", "OSM_HYDRO");

    public boolean requiresNameGrouping() {
        return POWER_PLANT_GROUPING.contains(dbCode);
    }

    public BigDecimal getWeightBD() {
        return BigDecimal.valueOf(weight);
    }

    public boolean validate(Map<String, String> tags) {
        return validator.test(tags);
    }
}

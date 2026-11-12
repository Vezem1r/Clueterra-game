package io.clueterra.budget.source.wikidata;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import io.clueterra.budget.project.MegaprojectCategory;

@Component
public class WikidataCategoryRelevancePolicy {

  private static final List<String> ENERGY_TERMS =
      List.of(
          "power",
          "energy",
          "electric",
          "nuclear",
          "solar",
          "wind",
          "hydro",
          "generating",
          "station",
          "plant",
          "wind farm",
          "dam",
          "reactor");
  private static final List<String> ROAD_TERMS =
      List.of("road", "highway", "motorway", "expressway", "freeway", "turnpike");
  private static final List<String> PORT_TERMS =
      List.of("port", "harbour", "harbor", "seaport", "container terminal", "marine terminal");

  public boolean isRelevant(MegaprojectCategory category, String name, String description) {
    String text = (name + " " + description).toLowerCase(Locale.ROOT);
    return switch (category) {
      case ENERGY -> ENERGY_TERMS.stream().anyMatch(text::contains);
      case ROAD_HIGHWAY -> ROAD_TERMS.stream().anyMatch(text::contains);
      case PORT -> PORT_TERMS.stream().anyMatch(text::contains);
      default -> true;
    };
  }
}

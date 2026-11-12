package io.clueterra.budget.project;

import java.util.List;

public enum MegaprojectCategory {
  BRIDGE("Q12280"),
  METRO_RAIL("Q5503", "Q728937"),
  STADIUM("Q483110"),
  SKYSCRAPER("Q11303"),
  AIRPORT("Q1248784"),
  TUNNEL("Q44377"),
  DAM("Q12323"),
  ENERGY("Q159719", "Q134447", "Q15911738", "Q194356", "Q2298412"),
  SPACE("Q60054001", "Q2133344", "Q25956", "Q148578", "Q40218", "Q697175"),
  ROAD_HIGHWAY("Q46622", "Q14875255", "Q269949", "Q16939396"),
  PORT("Q44782", "Q15310171", "Q1449240", "Q863915");

  private final List<String> rootIds;

  MegaprojectCategory(String... rootIds) {
    this.rootIds = List.of(rootIds);
  }

  public List<String> rootIds() {
    return rootIds;
  }
}

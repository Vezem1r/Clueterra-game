package io.clueterra.api.common.enums;

import lombok.Getter;

@Getter
public enum ChallengeLevel {
  EASY(100),
  MEDIUM(200),
  HARD(400);

  private final int baseXp;

  ChallengeLevel(int baseXp) {
    this.baseXp = baseXp;
  }

}

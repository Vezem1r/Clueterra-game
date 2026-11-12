package io.clueterra.questions.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChallengeCronProperties {
    private String dailyGeneration = "0 0 0 * * *";
    private String weeklyPreGeneration = "0 0 7 * * SUN";
    private String oldChallengesCleanup = "0 0 3 * * *";
}

package io.clueterra.questions.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChallengeProperties {
    private int reuseCooldownDays = 30;
    private int upcomingDaysToGenerate = 7;
    private int oldChallengesRetentionDays = 30;
    private ChallengeCronProperties cron = new ChallengeCronProperties();
}

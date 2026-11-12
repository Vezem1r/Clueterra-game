package io.clueterra.questions.entity;

import java.time.Instant;
import java.time.LocalDate;

import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.Difficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyChallenge {

    private Long id;
    private LocalDate challengeDate;

    @Builder.Default
    private ChallengeLevel challengeLevel = ChallengeLevel.MEDIUM;

    private String countriesJson;
    private String hintsJson;

    private Difficulty difficulty;

    @Builder.Default
    private Integer totalCountries = 5;

    @Builder.Default
    private Integer totalHints = 25;

    @Builder.Default
    private Boolean isActive = true;

    private Instant createdAt;
}

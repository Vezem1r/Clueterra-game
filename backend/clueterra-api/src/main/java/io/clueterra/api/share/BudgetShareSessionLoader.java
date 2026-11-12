package io.clueterra.api.share;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.clueterra.api.budget.model.BudgetGameSessionStatus;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;
import io.clueterra.api.share.dto.BudgetShareBuilding;
import io.clueterra.api.share.dto.BudgetShareData;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetShareSessionLoader {

    private final BudgetGameSessionJdbcRepository sessionRepository;

    public Optional<BudgetShareData> load(long sessionId) {
        return sessionRepository.findSession(sessionId)
                .filter(session -> session.status() == BudgetGameSessionStatus.COMPLETED)
                .flatMap(session -> {
                    var answers = sessionRepository.findAnswers(session.id()).stream()
                            .sorted(Comparator.comparingInt(answer -> answer.position()))
                            .toList();
                    if (answers.isEmpty()) {
                        return Optional.empty();
                    }

                    var buildings = answers.stream()
                            .map(answer -> new BudgetShareBuilding(
                                    answer.position(),
                                    buildingStyle(session.challengeDate(), answer.position()),
                                    answer.accuracyPercent().doubleValue(),
                                    answer.xpEarned()))
                            .toList();
                    double averageAccuracy = buildings.stream()
                            .mapToDouble(BudgetShareBuilding::accuracyPercent)
                            .average()
                            .orElse(0);
                    int maximumXp = session.baseXpPerQuestion() * buildings.size();

                    return Optional.of(new BudgetShareData(
                            session.challengeDate(),
                            session.totalXpEarned(),
                            maximumXp,
                            averageAccuracy,
                            buildings));
                });
    }

    private int buildingStyle(LocalDate challengeDate, int position) {
        int seed = challengeDate.toString().chars()
                .reduce(17, (value, character) -> Math.floorMod(value * 31 + character, 8));
        return Math.floorMod(seed + (position - 1) * 5, 8);
    }
}

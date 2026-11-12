package io.clueterra.questions.quizgame.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import io.clueterra.questions.quizgame.model.QuizCountryProfile;
import io.clueterra.questions.quizgame.model.QuizKind;

@Component
public class QuizDistractors {

    private static final int SAME_SUBREGION = 0;
    private static final int SAME_REGION = 1;
    private static final int ELSEWHERE = 2;
    private static final String SEED_SEPARATOR = ":";

    public long seed(String iso, QuizKind kind) {
        return (iso + SEED_SEPARATOR + kind.name()).hashCode();
    }

    public List<QuizCountryProfile> ranked(QuizCountryProfile target, Collection<QuizCountryProfile> candidates, long seed) {
        List<QuizCountryProfile> ranked = candidates.stream()
                .filter(candidate -> !candidate.iso().equals(target.iso()))
                .sorted(Comparator.comparing(QuizCountryProfile::iso))
                .collect(ArrayList::new, List::add, List::addAll);
        Collections.shuffle(ranked, new Random(seed));
        ranked.sort(Comparator.comparingInt((QuizCountryProfile candidate) -> closeness(target, candidate))
                .thenComparingInt(candidate -> Math.abs(candidate.tier() - target.tier())));
        return ranked;
    }

    public <T> List<T> shuffled(List<T> items, long seed) {
        List<T> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled;
    }

    private int closeness(QuizCountryProfile target, QuizCountryProfile candidate) {
        if (candidate.entry().subregion().equals(target.entry().subregion())) {
            return SAME_SUBREGION;
        }
        return candidate.entry().region().equals(target.entry().region()) ? SAME_REGION : ELSEWHERE;
    }
}

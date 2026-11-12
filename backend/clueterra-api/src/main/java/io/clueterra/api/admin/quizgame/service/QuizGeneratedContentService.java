package io.clueterra.api.admin.quizgame.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.quizgame.dto.QuizStopPreviewDto;
import io.clueterra.api.admin.quizgame.dto.QuizStopPreviewPage;
import io.clueterra.api.admin.quizgame.repository.QuizGeneratedContentJdbcRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizGeneratedContentService {

    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String ALL_KINDS = "ALL";
    private static final String NO_KIND = "";

    private final QuizGeneratedContentJdbcRepository repository;

    public QuizStopPreviewPage findStops(
            String countryCode, String search, String status, Integer tier, String kind, String sort, int page, int size) {
        Boolean active = switch (status) {
            case ACTIVE -> true;
            case INACTIVE -> false;
            default -> null;
        };
        List<QuizStopPreviewDto> stops = repository.findStops(countryCode, search, active, tier,
                ALL_KINDS.equals(kind) ? NO_KIND : kind, sort, size + 1, page * size);
        return new QuizStopPreviewPage(stops.stream().limit(size).toList(), page, size, stops.size() > size,
                repository.findSummary());
    }
}

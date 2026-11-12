package io.clueterra.api.admin.flaggame.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.flaggame.dto.FlagQuestionPreviewDto;
import io.clueterra.api.admin.flaggame.dto.FlagQuestionPreviewPage;
import io.clueterra.api.admin.flaggame.repository.FlagGeneratedContentJdbcRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlagGeneratedContentService {

    private final FlagGeneratedContentJdbcRepository repository;

    public FlagQuestionPreviewPage findQuestions(
            String countryCode, String search, String status, Integer tier, String capital, String sort, int page, int size) {
        Boolean active = switch (status) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> null;
        };
        List<FlagQuestionPreviewDto> questions =
                repository.findQuestions(countryCode, search, active, tier, capital, sort, size + 1, page * size);
        return new FlagQuestionPreviewPage(questions.stream().limit(size).toList(), page, size,
                questions.size() > size, repository.findSummary());
    }
}

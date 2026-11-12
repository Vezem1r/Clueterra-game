package io.clueterra.api.admin.silhouettegame.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewDto;
import io.clueterra.api.admin.silhouettegame.dto.SilhouetteQuestionPreviewPage;
import io.clueterra.api.admin.silhouettegame.repository.SilhouetteGeneratedContentJdbcRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SilhouetteGeneratedContentService {

    private final SilhouetteGeneratedContentJdbcRepository repository;

    public SilhouetteQuestionPreviewPage findQuestions(
            String countryCode, String search, String status, Integer tier, String photo, String sort, int page, int size) {
        Boolean active = switch (status) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> null;
        };
        List<SilhouetteQuestionPreviewDto> questions =
                repository.findQuestions(countryCode, search, active, tier, photo, sort, size + 1, page * size);
        return new SilhouetteQuestionPreviewPage(questions.stream().limit(size).toList(), page, size,
                questions.size() > size, repository.findSummary());
    }
}

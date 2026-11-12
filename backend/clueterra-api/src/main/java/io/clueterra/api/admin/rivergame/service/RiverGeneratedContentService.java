package io.clueterra.api.admin.rivergame.service;

import io.clueterra.api.admin.rivergame.dto.RiverQuestionPreviewPage;
import io.clueterra.api.admin.rivergame.repository.RiverGeneratedContentJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RiverGeneratedContentService {

    private final RiverGeneratedContentJdbcRepository repository;

    public RiverQuestionPreviewPage findQuestions(
            String countryCode, String search, String status, String sort, int page, int size) {
        Boolean active = switch (status) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> null;
        };
        long total = repository.countQuestions(countryCode, search, active);
        return new RiverQuestionPreviewPage(
                repository.findQuestions(countryCode, search, active, sort, size, page * size), total, page, size);
    }
}

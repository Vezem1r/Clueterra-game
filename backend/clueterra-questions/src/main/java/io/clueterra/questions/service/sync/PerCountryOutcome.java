package io.clueterra.questions.service.sync;

import io.clueterra.questions.service.operation.OperationSummary;

import java.util.List;

public record PerCountryOutcome(int processed, List<String> errors, int total) {

    OperationSummary toSummary(String totalKey) {
        return OperationSummary.of("countriesProcessed", processed)
                .and("countriesFailed", errors.size())
                .and(totalKey, total)
                .and("errors", errors);
    }
}

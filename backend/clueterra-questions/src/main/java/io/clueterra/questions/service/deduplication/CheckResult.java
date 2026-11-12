package io.clueterra.questions.service.deduplication;

import io.clueterra.questions.service.deduplication.dto.RejectedHint;

public record CheckResult(CheckOutcome outcome, RejectedHint rejection) {

    static CheckResult accepted() {
        return new CheckResult(CheckOutcome.ACCEPTED, null);
    }

    static CheckResult refresh() {
        return new CheckResult(CheckOutcome.METADATA_REFRESH, null);
    }

    static CheckResult rejected(RejectedHint rejection) {
        return new CheckResult(CheckOutcome.REJECTED, rejection);
    }
}

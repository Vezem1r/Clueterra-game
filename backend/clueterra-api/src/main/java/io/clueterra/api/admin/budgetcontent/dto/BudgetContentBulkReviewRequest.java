package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BudgetContentBulkReviewRequest(
        @NotEmpty @Size(max = 500) List<@Valid BudgetContentCandidateKey> candidates,
        @NotNull BudgetContentReviewStatus status,
        @Size(max = 1000) String note) {
}

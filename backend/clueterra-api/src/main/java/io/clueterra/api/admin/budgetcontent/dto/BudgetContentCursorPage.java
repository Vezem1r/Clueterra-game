package io.clueterra.api.admin.budgetcontent.dto;

import java.util.List;

public record BudgetContentCursorPage(
        List<BudgetContentCandidateDto> content, String nextCursor, boolean hasMore) {
}

package io.clueterra.questions.service.deduplication.dto;

public enum RejectionReason {
    EXACT_DUPLICATE,
    HIGH_SIMILARITY,
    SAME_INDICATOR_PATTERN,
    LOW_QUALITY,
    EMPTY_TEXT
}

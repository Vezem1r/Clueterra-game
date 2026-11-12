package io.clueterra.questions.clients.common.dto;

import java.util.List;

public record FetchResult(int totalFetched, List<FetchFailure> failures) {
}

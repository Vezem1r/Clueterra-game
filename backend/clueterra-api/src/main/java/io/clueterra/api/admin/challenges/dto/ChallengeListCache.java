package io.clueterra.api.admin.challenges.dto;

import java.util.List;

public record ChallengeListCache(List<ChallengeAdminDto> content, long totalElements) {
}

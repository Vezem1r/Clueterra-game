package io.clueterra.api.admin.challenges.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record ChallengePageResponse(
        List<ChallengeAdminDto> pinned,
        List<ChallengeAdminDto> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {

    public static ChallengePageResponse of(List<ChallengeAdminDto> pinned, Page<ChallengeAdminDto> page) {
        return new ChallengePageResponse(pinned, page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}

package io.clueterra.api.admin.users.dto;

import jakarta.validation.constraints.NotNull;

public record GrantBadgeRequest(@NotNull Long badgeId) {
}

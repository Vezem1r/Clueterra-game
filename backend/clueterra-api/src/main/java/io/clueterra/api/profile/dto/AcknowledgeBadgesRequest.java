package io.clueterra.api.profile.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record AcknowledgeBadgesRequest(@NotNull List<String> badgeIds) {
}

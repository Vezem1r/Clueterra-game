package io.clueterra.api.admin.badges.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ReorderRequest(@NotEmpty List<String> orderedIds) {
}

package io.clueterra.api.silhouettegame.model;

import io.clueterra.api.silhouettegame.dto.SilhouetteSessionDto;

public record SilhouetteGuestStartResult(String guestToken, SilhouetteSessionDto session) {
}

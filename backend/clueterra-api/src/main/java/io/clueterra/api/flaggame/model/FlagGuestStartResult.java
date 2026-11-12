package io.clueterra.api.flaggame.model;

import io.clueterra.api.flaggame.dto.FlagSessionDto;

public record FlagGuestStartResult(String guestToken, FlagSessionDto session) {
}

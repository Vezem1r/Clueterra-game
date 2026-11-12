package io.clueterra.api.rivergame.model;

import io.clueterra.api.rivergame.dto.RiverQuestionDto;

public record RiverGuestStartResult(String guestToken, RiverQuestionDto question) {
}

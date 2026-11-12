package io.clueterra.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.clueterra.api.common.enums.SessionStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuestStateDto(
        boolean hasGuest,
        boolean needsLegalAcceptance,
        boolean hasTodaySession,
        @JsonSerialize(using = ToStringSerializer.class) Long todaySessionId,
        SessionStatus todaySessionStatus) {

    public static GuestStateDto none() {
        return new GuestStateDto(false, false, false, null, null);
    }
}

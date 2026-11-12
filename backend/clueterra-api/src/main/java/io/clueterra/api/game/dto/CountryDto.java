package io.clueterra.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CountryDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String code,
        String name,
        String flagEmoji,
        String flagUrl,
        String region) {
}

package io.clueterra.api.admin.hints.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record HintCountryOption(
        @JsonSerialize(using = ToStringSerializer.class) Long id, String isoAlpha2, String nameEn) {
}

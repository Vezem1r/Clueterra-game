package io.clueterra.api.admin.countries.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CountryAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String isoAlpha2,
        String isoAlpha3,
        String nameEn,
        String nameNative,
        String region,
        String subregion,
        String capital,
        String flagEmoji,
        String flagUrl,
        BigDecimal difficultyModifier,
        Integer timesFeatured,
        Integer timesGuessed,
        BigDecimal successRate,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long hintCount,
        Long statisticCount) {

    public CountryAdminDto withCounts(Long hintCount, Long statisticCount) {
        return new CountryAdminDto(
                id,
                isoAlpha2,
                isoAlpha3,
                nameEn,
                nameNative,
                region,
                subregion,
                capital,
                flagEmoji,
                flagUrl,
                difficultyModifier,
                timesFeatured,
                timesGuessed,
                successRate,
                isActive,
                createdAt,
                updatedAt,
                hintCount,
                statisticCount);
    }
}

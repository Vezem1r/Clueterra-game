package io.clueterra.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HintDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String text,
        int difficulty,
        String category,
        String type,
        String source,
        String imageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl) {

    public HintDto(
            Long id,
            String text,
            int difficulty,
            String category,
            String type,
            String source,
            String imageUrl) {
        this(id, text, difficulty, category, type, source, imageUrl,
                null, null, null, null, null);
    }
}

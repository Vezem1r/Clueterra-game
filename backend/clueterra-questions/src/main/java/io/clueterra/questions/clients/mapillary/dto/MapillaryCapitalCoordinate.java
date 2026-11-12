package io.clueterra.questions.clients.mapillary.dto;

import java.math.BigDecimal;

public record MapillaryCapitalCoordinate(
        String countryCode, String capitalName, BigDecimal latitude, BigDecimal longitude) {}

package io.clueterra.api.projectpages.service;

public record ProjectCostView(
        String label,
        String amount,
        String usdEquivalent,
        Integer year,
        boolean featured,
        String scope,
        String sourceName,
        String sourceUrl) {
}

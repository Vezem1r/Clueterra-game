package io.clueterra.questions.clients.fastfood.dto;

import java.util.List;
import java.util.Map;

public record CountryFastFood(
        Map<String, ChainInfo> chains,
        List<SignatureItem> signatureItems,
        LocalChampion localChampion,
        List<String> culturalFacts,
        List<String> absentChains,
        Map<String, String> specialFacts
) {
    public CountryFastFood {
        chains = chains != null ? chains : Map.of();
        signatureItems = signatureItems != null ? signatureItems : List.of();
        culturalFacts = culturalFacts != null ? culturalFacts : List.of();
        absentChains = absentChains != null ? absentChains : List.of();
        specialFacts = specialFacts != null ? specialFacts : Map.of();
    }
}

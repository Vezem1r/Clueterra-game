package io.clueterra.questions.clients.common;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractDynamicClient extends AbstractSourceClient {

    @Override
    public boolean isDynamic() {
        return true;
    }

    protected List<RawCountryData> buildResult(Map<String, List<RawStatistic>> enriched) {
        List<RawCountryData> result = new ArrayList<>();
        for (Map.Entry<String, List<RawStatistic>> entry : enriched.entrySet()) {
            result.add(
                    RawCountryData.builder()
                            .isoAlpha2(entry.getKey())
                            .nameEn(entry.getKey())
                            .source(getSource())
                            .statistics(entry.getValue())
                            .build());
        }
        return result;
    }

    protected Optional<RawCountryData> buildResultOne(
            String iso2, Map<String, List<RawStatistic>> enriched) {
        List<RawStatistic> stats = enriched.get(iso2);
        if (stats == null || stats.isEmpty()) return Optional.empty();
        return Optional.of(
                RawCountryData.builder()
                        .isoAlpha2(iso2)
                        .nameEn(iso2)
                        .source(getSource())
                        .statistics(stats)
                        .build());
    }
}

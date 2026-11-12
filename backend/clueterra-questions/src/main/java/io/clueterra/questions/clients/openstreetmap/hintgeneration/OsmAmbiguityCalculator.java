package io.clueterra.questions.clients.openstreetmap.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OsmAmbiguityCalculator {

    public int computeDecile(
            BigDecimal value, String indicatorCode, Map<Long, List<CountryStatistic>> byCountry) {
        if (value == null) return 1;
        long v = value.longValue();
        List<Long> values = new ArrayList<>();
        for (List<CountryStatistic> stats : byCountry.values()) {
            for (CountryStatistic stat : stats) {
                if (indicatorCode.equals(stat.getIndicatorCode())
                        && stat.getValue() != null
                        && stat.getValue().longValue() > 0) {
                    values.add(stat.getValue().longValue());
                }
            }
        }
        if (values.size() < 2) return 1;
        Collections.sort(values);
        int size = values.size();
        int rank = upperBound(values, v);
        int decile = rank * 10 / size;
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (i * 10 / size == decile) count++;
        }
        return Math.max(1, count);
    }

    public int computeRankBand(int rank, int total) {
        if (rank <= 3) return rank;
        if (rank <= 10) return 5;
        if (rank <= 25) return 15;
        if (rank <= 50) return 25;
        return Math.max(1, total / 4);
    }

    public int computeExactTuple(Map<String, Long> tuple, Map<Long, List<CountryStatistic>> byCountry) {
        if (tuple == null || tuple.isEmpty()) return 1;

        int matches = 0;
        for (List<CountryStatistic> stats : byCountry.values()) {
            boolean sameTuple = true;
            for (Map.Entry<String, Long> entry : tuple.entrySet()) {
                long countryValue = findLongValue(stats, entry.getKey());
                if (countryValue != entry.getValue()) {
                    sameTuple = false;
                    break;
                }
            }
            if (sameTuple) {
                matches++;
            }
        }
        return Math.max(1, matches);
    }

    private long findLongValue(List<CountryStatistic> stats, String indicatorCode) {
        for (CountryStatistic stat : stats) {
            if (indicatorCode.equals(stat.getIndicatorCode()) && stat.getValue() != null) {
                return stat.getValue().longValue();
            }
        }
        return 0L;
    }

    private int upperBound(List<Long> sorted, long v) {
        int lo = 0;
        int hi = sorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted.get(mid) <= v) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}

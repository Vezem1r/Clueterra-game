package io.clueterra.api.admin.analytics.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.analytics.dto.AggregationResultDto;
import io.clueterra.api.admin.analytics.dto.AnalyticsSummaryDto;
import io.clueterra.api.admin.analytics.dto.DailySessionDto;
import io.clueterra.api.admin.analytics.dto.SessionsByLevelDto;
import io.clueterra.api.admin.analytics.dto.TopCountryDto;
import io.clueterra.api.admin.analytics.repository.AnalyticsJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsJdbcRepository analyticsRepo;

    public AnalyticsSummaryDto getSummary(LocalDate startDate, LocalDate endDate) {
        return analyticsRepo.getSummary(startDate, endDate);
    }

    public List<SessionsByLevelDto> getSessionsByLevel(LocalDate startDate, LocalDate endDate) {
        return analyticsRepo.getSessionsByLevel(startDate, endDate);
    }

    public List<TopCountryDto> getTopCountries(LocalDate startDate, LocalDate endDate, int limit) {
        return analyticsRepo.getTopCountries(startDate, endDate, limit);
    }

    public List<TopCountryDto> getHardestCountries(LocalDate startDate, LocalDate endDate, int limit) {
        return analyticsRepo.getHardestCountries(startDate, endDate, limit);
    }

    public List<DailySessionDto> getDailySessions(LocalDate startDate, LocalDate endDate) {
        return analyticsRepo.getDailySessions(startDate, endDate);
    }

    public void aggregateDate(LocalDate date) {
        try {
            analyticsRepo.aggregateDate(date);
            log.info("Analytics aggregated successfully for date: {}", date);
        } catch (Exception e) {
            log.error("Failed to aggregate analytics for date: {}", date, e);
            throw e;
        }
    }

    public AggregationResultDto aggregateRange(LocalDate startDate, LocalDate endDate) {
        int days = 0;
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            aggregateDate(d);
            days++;
        }
        return new AggregationResultDto(days, "OK");
    }
}

package io.clueterra.api.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.clueterra.api.common.enums.ReportType;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.reports.dto.HintReportRequest;
import io.clueterra.api.reports.dto.PreInsertChecks;
import io.clueterra.api.reports.repository.HintReportJdbcRepository;

@ExtendWith(MockitoExtension.class)
class HintReportServiceTest {

    private static final long USER_ID = 42L;
    private static final long HINT_ID = 829113101073225714L;

    @Mock
    private HintReportJdbcRepository reportRepository;

    private HintReportService service;

    @BeforeEach
    void setUp() {
        service = new HintReportService(reportRepository);
        ReflectionTestUtils.setField(service, "maxPendingPerUser", 5);
    }

    @Test
    @DisplayName("report accepts historical hint ids when they still exist in live or archive storage")
    void report_acceptsHistoricalHintId() {
        when(reportRepository.preInsertChecks(HINT_ID, USER_ID))
                .thenReturn(new PreInsertChecks(false, 0, false, true));
        when(reportRepository.insert(eq(HINT_ID), eq(USER_ID), eq("OTHER"), eq("stale clue")))
                .thenReturn(123L);

        var response = service.report(USER_ID, new HintReportRequest(HINT_ID, ReportType.OTHER, "stale clue"));

        assertThat(response.success()).isTrue();
        assertThat(response.reportId()).isEqualTo("123");
        verify(reportRepository).insert(HINT_ID, USER_ID, "OTHER", "stale clue");
    }

    @Test
    @DisplayName("report returns not found when hint is missing from both live and archived hint storage")
    void report_rejectsUnknownHint() {
        when(reportRepository.preInsertChecks(HINT_ID, USER_ID))
                .thenReturn(new PreInsertChecks(false, 0, false, false));

        assertThatThrownBy(() -> service.report(USER_ID, new HintReportRequest(HINT_ID, ReportType.OTHER, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hint not found");

        verify(reportRepository, never()).insert(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("report rejects duplicate reports before insert")
    void report_rejectsDuplicate() {
        when(reportRepository.preInsertChecks(HINT_ID, USER_ID))
                .thenReturn(new PreInsertChecks(true, 0, false, true));

        assertThatThrownBy(() -> service.report(USER_ID, new HintReportRequest(HINT_ID, ReportType.OTHER, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You have already reported this hint");

        verify(reportRepository, never()).insert(anyLong(), anyLong(), anyString(), any());
    }
}

package io.clueterra.api.admin.budgetcontent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoStatus;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationResult;
import io.clueterra.api.admin.budgetcontent.repository.BudgetProjectSeoEditorialContext;
import io.clueterra.api.admin.budgetcontent.repository.BudgetProjectSeoJdbcRepository;
import io.clueterra.api.common.dto.PageResponse;

@ExtendWith(MockitoExtension.class)
class BudgetProjectSeoServiceTest {

    @Mock
    private BudgetProjectSeoJdbcRepository repository;

    private BudgetProjectSeoService service;

    @BeforeEach
    void setUp() {
        service = new BudgetProjectSeoService(repository);
    }

    @Test
    void paginatesAndNormalizesEditorialSearch() {
        when(repository.count("mars", "PUBLISHED")).thenReturn(50L);
        when(repository.findPage("mars", "PUBLISHED", 24, 24)).thenReturn(List.of());

        PageResponse<?> result = service.findPage(1, 24, "  MARS  ", "published");

        assertThat(result.number()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(24);
        assertThat(result.totalElements()).isEqualTo(50);
        assertThat(result.totalPages()).isEqualTo(3);
        verify(repository).findPage("mars", "PUBLISHED", 24, 24);
    }

    @Test
    void blocksPublishingWhenReviewedProjectIsUnavailable() {
        BudgetProjectSeoPageRequest request = emptyRequest(BudgetProjectSeoStatus.PUBLISHED);
        when(repository.findEditorialContext("Q123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save("Q123", request, "admin@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Fix");

        verify(repository, never()).upsert(any(), any(), any());
    }

    @Test
    void allowsAnIncompleteDraftToBeSaved() {
        BudgetProjectSeoPageRequest request = emptyRequest(BudgetProjectSeoStatus.DRAFT);
        BudgetProjectSeoPageDto saved = new BudgetProjectSeoPageDto(
                "Q123",
                "Draft project",
                "Testland",
                "BRIDGE",
                true,
                true,
                null,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                BudgetProjectSeoStatus.DRAFT,
                null,
                "admin@example.com",
                Instant.parse("2026-09-20T12:00:00Z"));
        when(repository.upsert(eq("Q123"), eq(request), eq("admin@example.com")))
                .thenReturn(Optional.of(saved));

        assertThat(service.save("Q123", request, "admin@example.com")).isEqualTo(saved);
        verify(repository, never()).findEditorialContext(any());
    }

    @Test
    void blocksPublicationWhenPrimaryCostEvidenceIsUnreferenced() {
        BudgetProjectSeoEditorialContext context = validContext("UNREFERENCED_KNOWLEDGE_BASE");
        when(repository.findEditorialContext("Q123")).thenReturn(Optional.of(context));

        BudgetProjectSeoValidationResult result = service.validate("Q123", validRequest());

        assertThat(result.publishable()).isFalse();
        assertThat(result.issues()).extracting("code").contains("WEAK_COST_EVIDENCE");
    }

    @Test
    void blocksPublicationWhenSourceLabelContradictsExternalUrl() {
        when(repository.findEditorialContext("Q123"))
                .thenReturn(Optional.of(validContext("OFFICIAL_SOURCE")));
        BudgetProjectSeoPageRequest request = new BudgetProjectSeoPageRequest(
                "example-bridge",
                "How much did Example Bridge cost?",
                "How much did Example Bridge cost? | Clueterra",
                "Explore the reported cost of Example Bridge, what the amount includes, key project facts and the reviewed source behind the figure.",
                "Example Bridge cost, bridge construction cost, Testland infrastructure",
                "Example Bridge connects two important regions in Testland. The page explains the reviewed construction figure and places it alongside the bridge's verified characteristics and timeline.",
                "The reviewed authority reports a construction cost of one hundred million US dollars for Example Bridge. The amount is presented in its original currency and source basis.",
                "The reported figure covers the original construction contract. It excludes later maintenance, replacement work, operating expenses and unrelated improvements to the wider road network.",
                "The crossing was developed to improve travel between the two regions and forms part of a significant transport corridor. Its scale and opening date provide context for the cost.",
                "Clueterra preserves the amount, currency and year given by the reviewed source. It does not silently convert the historical figure into a current replacement value.",
                "Wikipedia",
                "https://www.eia.gov/nuclear/state/archive/2010/ohio/",
                List.of(),
                List.of(),
                BudgetProjectSeoStatus.PUBLISHED);

        BudgetProjectSeoValidationResult result = service.validate("Q123", request);

        assertThat(result.publishable()).isFalse();
        assertThat(result.issues()).extracting("code").contains("SOURCE_NAME_URL_MISMATCH");
    }

    private BudgetProjectSeoPageRequest validRequest() {
        return new BudgetProjectSeoPageRequest(
                "example-bridge",
                "How much did Example Bridge cost?",
                "How much did Example Bridge cost? | Clueterra",
                "Explore the reported cost of Example Bridge, what the amount includes, key project facts and the reviewed source behind the figure.",
                "Example Bridge cost, bridge construction cost, Testland infrastructure",
                "Example Bridge connects two important regions in Testland. The page explains the reviewed construction figure and places it alongside the bridge's verified characteristics and timeline.",
                "The reviewed authority reports a construction cost of one hundred million US dollars for Example Bridge. The amount is presented in its original currency and source basis.",
                "The reported figure covers the original construction contract. It excludes later maintenance, replacement work, operating expenses and unrelated improvements to the wider road network.",
                "The crossing was developed to improve travel between the two regions and forms part of a significant transport corridor. Its scale and opening date provide context for the cost.",
                "Clueterra preserves the amount, currency and year given by the reviewed source. It does not silently convert the historical figure into a current replacement value.",
                "Example authority",
                "https://example.com/cost",
                List.of(),
                List.of(),
                BudgetProjectSeoStatus.PUBLISHED);
    }

    private BudgetProjectSeoPageRequest emptyRequest(BudgetProjectSeoStatus status) {
        return new BudgetProjectSeoPageRequest(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                status);
    }

    private BudgetProjectSeoEditorialContext validContext(String evidenceTier) {
        return new BudgetProjectSeoEditorialContext(
                "Q123",
                "Example Bridge",
                "Example Bridge is a long-span crossing built to connect two regions and improve a major transport corridor. This description is intentionally complete enough for editorial validation.",
                "Testland",
                "BRIDGE",
                2020,
                new BigDecimal("100000000"),
                new BigDecimal("100000000"),
                "USD",
                2020,
                "The source amount is already denominated in US dollars and is not inflation-adjusted.",
                "Example authority",
                "https://example.com/cost",
                evidenceTier,
                90,
                false,
                true,
                true,
                true,
                "VERIFIED",
                "The authority reports a construction cost of one hundred million US dollars.",
                "The figure covers the original construction contract and excludes later maintenance.",
                2,
                1);
    }
}

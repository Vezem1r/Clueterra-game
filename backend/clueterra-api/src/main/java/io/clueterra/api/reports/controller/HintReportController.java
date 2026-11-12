package io.clueterra.api.reports.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.reports.dto.HintReportRequest;
import io.clueterra.api.reports.dto.HintReportResponse;
import io.clueterra.api.reports.service.HintReportService;
import io.clueterra.api.security.RegisteredUserOnly;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hints")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Hint reporting for registered users")
public class HintReportController {

    private final HintReportService reportService;

    @PostMapping("/report")
    @RegisteredUserOnly
    public ResponseEntity<HintReportResponse> reportHint(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody HintReportRequest request) {
        return ResponseEntity.ok(reportService.report(principal.getId(), request));
    }
}

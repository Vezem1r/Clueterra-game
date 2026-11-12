package io.clueterra.api.admin.budgetcontent.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.projectpages.service.ProjectDetailPageResponse;
import io.clueterra.api.projectpages.service.ProjectPageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/budget-content/seo-pages")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Budget project SEO", description = "Editorial publication of project pages")
public class AdminBudgetProjectSeoPreviewController {

    private final ProjectPageService projectPageService;

    @GetMapping(value = "/{wikidataId}/preview-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a saved project SEO draft for the private React preview")
    public ResponseEntity<ProjectDetailPageResponse> previewData(
            @PathVariable
            @Pattern(regexp = "^Q[1-9][0-9]*$")
            String wikidataId) {
        ProjectDetailPageResponse projectPage = projectPageService.findProjectPreview(wikidataId)
                .map(project -> new ProjectDetailPageResponse(
                        project,
                        projectPageService.findRelated(project, 3)))
                .orElseThrow(() -> new ResourceNotFoundException("Project page draft not found"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(projectPage);
    }
}

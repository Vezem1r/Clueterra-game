package io.clueterra.api.admin.badges.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.badges.dto.BadgeCategoryDto;
import io.clueterra.api.admin.badges.dto.CreateCategoryRequest;
import io.clueterra.api.admin.badges.dto.ReorderRequest;
import io.clueterra.api.admin.badges.service.AdminBadgeCategoryService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/badge-categories")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Badge Categories", description = "Badge category management")
public class AdminBadgeCategoryController {

    private final AdminBadgeCategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<BadgeCategoryDto>> list() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @PostMapping
    public ResponseEntity<BadgeCategoryDto> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request.name()));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        List<Long> ids = request.orderedIds().stream().map(Long::parseLong).toList();
        categoryService.reorder(ids);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BadgeCategoryDto> rename(@PathVariable Long id,
                                                   @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.rename(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

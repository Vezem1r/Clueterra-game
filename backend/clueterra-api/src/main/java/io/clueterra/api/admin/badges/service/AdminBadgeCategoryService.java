package io.clueterra.api.admin.badges.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.badges.dto.BadgeCategoryDto;
import io.clueterra.api.admin.badges.repository.AdminBadgeCategoryJdbcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminBadgeCategoryService {

    private final AdminBadgeCategoryJdbcRepository categoryRepository;

    public List<BadgeCategoryDto> findAll() {
        return categoryRepository.findAll();
    }

    public BadgeCategoryDto create(String name) {
        return categoryRepository.insert(name.trim());
    }

    public BadgeCategoryDto rename(Long id, String name) {
        int rows = categoryRepository.rename(id, name.trim());
        if (rows == 0) throw new IllegalArgumentException("Category not found: " + id);
        return categoryRepository.findAll().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    public void delete(Long id) {
        if (categoryRepository.hasAssignedBadges(id)) {
            throw new IllegalStateException("Cannot delete a category with assigned badges");
        }
        categoryRepository.delete(id);
    }

    public void reorder(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) throw new IllegalArgumentException("orderedIds must not be empty");
        categoryRepository.bulkUpdateCategoryOrder(orderedIds);
    }
}

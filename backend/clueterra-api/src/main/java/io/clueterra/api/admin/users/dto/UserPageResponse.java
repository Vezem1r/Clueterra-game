package io.clueterra.api.admin.users.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record UserPageResponse(
        List<AdminUserDto> pinned,
        List<AdminUserDto> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {

    public static UserPageResponse of(List<AdminUserDto> pinned, Page<AdminUserDto> page) {
        return new UserPageResponse(pinned, page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}

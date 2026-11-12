package io.clueterra.api.admin.users.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.admin.badges.dto.UserBadgeAdminDto;
import io.clueterra.api.admin.badges.service.AdminBadgeService;
import io.clueterra.api.admin.users.dto.AdminUserDto;
import io.clueterra.api.admin.users.dto.GrantBadgeRequest;
import io.clueterra.api.admin.users.dto.UpdateUserProfileRequest;
import io.clueterra.api.admin.users.dto.UpdateUserRequest;
import io.clueterra.api.admin.users.dto.UserPageResponse;
import io.clueterra.api.admin.users.service.UserService;
import io.clueterra.api.common.enums.GrantedBy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Users", description = "User management")
public class AdminUserController {

    private final UserService userService;
    private final AdminBadgeService badgeService;

    @GetMapping
    public ResponseEntity<UserPageResponse> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean reportsMuted,
            @RequestParam(defaultValue = "false") Boolean isGuest,
            @RequestParam(required = false) @Min(0) Integer minRiversPlayed,
            @RequestParam(required = false) @Min(0) Integer maxRiversPlayed,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(userService.listUsers(page, size, search, role, enabled, reportsMuted,
                isGuest, minRiversPlayed, maxRiversPlayed, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDto> getUser(@PathVariable Long id) {
        return userService.getUser(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<AdminUserDto> updateProfile(@PathVariable Long id, @Valid @RequestBody UpdateUserProfileRequest request) {
        return userService.updateProfile(id, request).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/avatar")
    public ResponseEntity<AdminUserDto> uploadAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return userService.uploadAvatar(id, file).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<Void> deleteAvatar(@PathVariable Long id) {
        userService.deleteAvatar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public ResponseEntity<List<String>> getRoles() {
        return ResponseEntity.ok(userService.getRoles());
    }

    @GetMapping("/{id}/badges")
    public ResponseEntity<List<UserBadgeAdminDto>> getUserBadges(@PathVariable Long id) {
        return ResponseEntity.ok(badgeService.getUserBadges(id));
    }

    @PostMapping("/{id}/badges")
    public ResponseEntity<Void> grantBadge(@PathVariable Long id, @Valid @RequestBody GrantBadgeRequest request) {
        badgeService.grantBadge(id, request.badgeId(), GrantedBy.ADMIN.name());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/badges/{badgeId}")
    public ResponseEntity<Void> revokeBadge(@PathVariable Long id, @PathVariable Long badgeId) {
        badgeService.revokeBadge(id, badgeId);
        return ResponseEntity.noContent().build();
    }
}

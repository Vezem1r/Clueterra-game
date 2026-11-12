package io.clueterra.api.auth.dto.github;

public record GitHubEmailResponse(
        String email,
        boolean primary,
        boolean verified,
        String visibility
) {
}

package io.clueterra.api.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String displayUsername;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final String termsVersionAccepted;
    private final String privacyVersionAccepted;
    private final boolean guest;

    public UserPrincipal(Long id, String email, String passwordHash, String username, String role, boolean enabled) {
        this(id, email, passwordHash, username, role, enabled, null, null, false);
    }

    public UserPrincipal(Long id, String email, String passwordHash, String username, String role, boolean enabled,
                         String termsVersionAccepted, String privacyVersionAccepted) {
        this(id, email, passwordHash, username, role, enabled, termsVersionAccepted, privacyVersionAccepted, false);
    }

    public UserPrincipal(Long id, String email, String passwordHash, String username, String role, boolean enabled,
                         String termsVersionAccepted, String privacyVersionAccepted, boolean guest) {
        this.id = id;
        this.email = email;
        this.password = passwordHash;
        this.displayUsername = username;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        this.enabled = enabled;
        this.termsVersionAccepted = termsVersionAccepted;
        this.privacyVersionAccepted = privacyVersionAccepted;
        this.guest = guest;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}

package io.clueterra.api.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AuthJdbcRepository authRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return authRepository.findByEmail(email)
                .map(row -> new UserPrincipal(
                        row.id(), row.email(), row.passwordHash(),
                        row.username(), row.role(), row.enabled(),
                        row.termsVersionAccepted(), row.privacyVersionAccepted()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}

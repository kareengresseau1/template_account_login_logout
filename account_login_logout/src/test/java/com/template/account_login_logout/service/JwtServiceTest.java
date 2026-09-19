package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

    private static final String SECRET = "VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdTZWNyZXRLZXlGb3JEZXZlbG9wbWVudA==";

    private final JwtService jwtService = new JwtService(SECRET, 60_000, 120_000);
    private final UserDetails user = User.withUsername("jane")
            .password("encoded-password")
            .authorities("ROLE_USER")
            .build();

    @Test
    void generatedAccessTokenContainsUsernameAndIsValid() {
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("jane");
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(60);
    }

    @Test
    void invalidTokenIsRejected() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void refreshTokenUsesConfiguredLifetime() {
        jwtService.generateRefreshToken(user);

        assertThat(jwtService.getRefreshTokenTtlMillis()).isEqualTo(120_000);
    }
}

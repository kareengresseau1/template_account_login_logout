package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.repository.RevokedTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    void tokenIsNotBlacklistedUntilItIsRevoked() {
        TokenBlacklistService service = new TokenBlacklistService(revokedTokenRepository, 900_000);
        when(revokedTokenRepository.existsByTokenHashAndExpiresAtAfter(anyString(), any(Instant.class)))
                .thenReturn(false, true);

        assertThat(service.isBlacklisted("access-token")).isFalse();

        service.blacklist("access-token");

        assertThat(service.isBlacklisted("access-token")).isTrue();
    }
}

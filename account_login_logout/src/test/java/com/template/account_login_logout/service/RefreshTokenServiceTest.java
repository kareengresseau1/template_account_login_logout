package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.model.RefreshToken;
import com.template.account_login_logout.repository.AccountRepository;
import com.template.account_login_logout.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccountRepository accountRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                accountRepository,
                120_000);
    }

    @Test
    void createsAndStoresRefreshTokenForAccount() {
        Account account = account("jane");
        when(accountRepository.findByUsername("jane")).thenReturn(Optional.of(account));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken result = refreshTokenService.createRefreshToken("jane", "refresh-token");

        assertThat(result.getUser()).isSameAs(account);
        assertThat(result.getToken()).isEqualTo("refresh-token");
        assertThat(result.getExpiryDate()).isAfter(Instant.now());
        verify(refreshTokenRepository).deleteByUser_Username("jane");
    }

    @Test
    void expiredRefreshTokenIsDeletedAndRejected() {
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(token))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessage("Refresh token has expired");
        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void missingAccountCannotCreateRefreshToken() {
        when(accountRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.createRefreshToken("missing", "refresh-token"))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessage("Account not found");
    }

    private Account account(String username) {
        Account account = new Account();
        account.setUsername(username);
        account.setEmail(username + "@example.com");
        return account;
    }
}

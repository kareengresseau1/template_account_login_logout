package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, passwordEncoder, emailService, refreshTokenService);
    }

    @Test
    void passwordResetStoresOnlyTokenHashAndRevokesAllSessions() throws Exception {
        Account account = account();
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new password")).thenReturn("new-password-hash");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.requestPasswordReset(account.getEmail());

        ArgumentCaptor<String> emailedToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(any(Account.class), emailedToken.capture());
        String rawToken = emailedToken.getValue();
        assertThat(account.getPasswordResetToken()).isEqualTo(hash(rawToken));
        assertThat(account.getPasswordResetToken()).isNotEqualTo(rawToken);

        account.setPasswordResetTokenExpiry(Instant.now().plusSeconds(60));
        accountService.resetPassword(account.getEmail(), rawToken, "new password");

        assertThat(account.getPassword()).isEqualTo("new-password-hash");
        assertThat(account.getPasswordResetToken()).isNull();
        verify(refreshTokenService).deleteAllForUser(account.getUsername());
    }

    @Test
    void emailVerificationStoresOnlyTokenHash() throws Exception {
        Account account = account();
        when(accountRepository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.requestEmailVerification(account.getEmail());

        ArgumentCaptor<String> emailedToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerificationEmail(any(Account.class), emailedToken.capture());
        assertThat(account.getEmailVerificationToken()).isEqualTo(hash(emailedToken.getValue()));
        assertThat(account.getEmailVerificationToken()).isNotEqualTo(emailedToken.getValue());
    }

    private Account account() {
        Account account = new Account();
        account.setUsername("jane");
        account.setEmail("jane@example.com");
        account.setPassword("old-password-hash");
        return account;
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}

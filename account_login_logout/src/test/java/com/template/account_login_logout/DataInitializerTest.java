package com.template.account_login_logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import com.template.account_login_logout.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments arguments;

    @Test
    void createsConfiguredAdminAndPurgesLegacySessions() {
        when(accountRepository.findByUsername("admin")).thenReturn(java.util.Optional.empty());
        when(accountRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-admin-password")).thenReturn("encoded-admin-password");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataInitializer initializer = new DataInitializer(
                accountRepository,
                refreshTokenRepository,
                passwordEncoder,
                "admin",
                "admin@example.com",
                "strong-admin-password",
                false,
                "",
                "",
                "");

        initializer.run(arguments);

        ArgumentCaptor<Account> savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedAccount.capture());
        assertThat(savedAccount.getValue().getUsername()).isEqualTo("admin");
        assertThat(savedAccount.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(savedAccount.getValue().getPassword()).isEqualTo("encoded-admin-password");
        assertThat(savedAccount.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(savedAccount.getValue().isEmailVerified()).isTrue();
        verify(refreshTokenRepository).deleteBySessionKeyIsNull();
    }

    @Test
    void requiresCredentialsWhenConfiguredAdminDoesNotExist() {
        when(accountRepository.findByUsername("admin")).thenReturn(java.util.Optional.empty());
        DataInitializer initializer = new DataInitializer(
                accountRepository,
                refreshTokenRepository,
                passwordEncoder,
                "admin",
                "",
                "",
                false,
                "",
                "",
                "");

        assertThatThrownBy(() -> initializer.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL");
    }

    @Test
    void seedsDemoAccountsIncludingConfiguredAccount() {
        when(accountRepository.findByUsername(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.existsByEmail(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataInitializer initializer = new DataInitializer(
                accountRepository,
                refreshTokenRepository,
                passwordEncoder,
                "",
                "",
                "",
                true,
                "krgresseau",
                "krgresseau@gmail.com",
                "temporary-password");

        initializer.run(arguments);

        ArgumentCaptor<Account> savedAccounts = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, org.mockito.Mockito.times(3)).save(savedAccounts.capture());
        assertThat(savedAccounts.getAllValues())
                .extracting(Account::getUsername)
                .containsExactly("jane", "alex", "krgresseau");
        assertThat(savedAccounts.getAllValues().get(2).getEmail()).isEqualTo("krgresseau@gmail.com");
        assertThat(savedAccounts.getAllValues().get(2).getPassword()).isEqualTo("encoded-temporary-password");
        assertThat(savedAccounts.getAllValues().get(2).isEmailVerified()).isTrue();
    }
}

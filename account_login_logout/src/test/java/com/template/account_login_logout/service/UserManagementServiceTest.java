package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(accountRepository, refreshTokenService);
    }

    @Test
    void disablingAccountRevokesItsSessions() {
        Account account = account();
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        Account updated = service.updateAccountStatus(7L, false);

        assertThat(updated.isEnabled()).isFalse();
        verify(refreshTokenService).deleteAllForUser("sam");
        verify(accountRepository).save(account);
    }

    @Test
    void enablingAccountDoesNotRevokeSessions() {
        Account account = account();
        account.setEnabled(false);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        assertThat(service.updateAccountStatus(7L, true).isEnabled()).isTrue();

        verify(refreshTokenService, never()).deleteAllForUser("sam");
    }

    @Test
    void roleChangesAcceptOnlyUserOrAdmin() {
        Account account = account();
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        assertThat(service.updateAccountRole(7L, "ADMIN").getRole()).isEqualTo("ADMIN");
        assertThatThrownBy(() -> service.updateAccountRole(7L, "OWNER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role must be USER or ADMIN");
    }

    private Account account() {
        Account account = new Account();
        account.setId(7L);
        account.setUsername("sam");
        return account;
    }
}

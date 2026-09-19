package com.template.account_login_logout.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadsUserByUsername() {
        Account account = account("jane", "encoded-password");
        when(accountRepository.findByUsername("jane")).thenReturn(Optional.of(account));

        var user = userDetailsService.loadUserByUsername("jane");

        assertThat(user.getUsername()).isEqualTo("jane");
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void fallsBackToEmailLookup() {
        Account account = account("jane", "encoded-password");
        when(accountRepository.findByUsername("jane@example.com")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(account));

        assertThat(userDetailsService.loadUserByUsername("jane@example.com").getUsername())
                .isEqualTo("jane");
    }

    @Test
    void rejectsUnknownLoginId() {
        when(accountRepository.findByUsername("missing")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private Account account(String username, String password) {
        Account account = new Account();
        account.setUsername(username);
        account.setEmail(username + "@example.com");
        account.setPassword(password);
        return account;
    }
}

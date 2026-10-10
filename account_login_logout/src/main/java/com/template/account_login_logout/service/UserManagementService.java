package com.template.account_login_logout.service;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {

    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;

    public UserManagementService(AccountRepository accountRepository, RefreshTokenService refreshTokenService) {
        this.accountRepository = accountRepository;
        this.refreshTokenService = refreshTokenService;
    }

    public List<Account> findAllAccounts() {
        return accountRepository.findAll();
    }

    public Optional<Account> findById(Long id) {
        return accountRepository.findById(id);
    }

    @Transactional
    public Account updateAccountStatus(Long userId, boolean enabled) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        account.setEnabled(enabled);
        if (!enabled) {
            refreshTokenService.deleteAllForUser(account.getUsername());
        }
        return accountRepository.save(account);
    }

    @Transactional
    public Account updateAccountRole(Long userId, String role) {
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        account.setRole(role);
        return accountRepository.save(account);
    }
}

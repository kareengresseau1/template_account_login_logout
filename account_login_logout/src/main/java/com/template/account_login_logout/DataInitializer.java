package com.template.account_login_logout;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import com.template.account_login_logout.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedDemoAccount;

    public DataInitializer(AccountRepository accountRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.username:}") String adminUsername,
                           @Value("${app.admin.email:}") String adminEmail,
                           @Value("${app.admin.password:}") String adminPassword,
                           @Value("${app.demo-seed.enabled:true}") boolean seedDemoAccount) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.seedDemoAccount = seedDemoAccount;
    }

    @Override
    public void run(ApplicationArguments args) {
        refreshTokenRepository.deleteBySessionKeyIsNull();

        if (seedDemoAccount && accountRepository.count() == 0) {
            Account account = new Account();
            account.setUsername("jane");
            account.setEmail("jane@example.com");
            account.setPassword(passwordEncoder.encode("password123"));
            accountRepository.save(account);
        }

        if (!adminUsername.isBlank()) {
            Account admin = accountRepository.findByUsername(adminUsername).orElseGet(() -> createAdmin());
            admin.setRole("ADMIN");
            accountRepository.save(admin);
        } else if (!adminEmail.isBlank() || !adminPassword.isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME is required with ADMIN_EMAIL or ADMIN_PASSWORD");
        }
    }

    private Account createAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "To create an admin account, set ADMIN_USERNAME, ADMIN_EMAIL, and ADMIN_PASSWORD");
        }
        if (accountRepository.existsByEmail(adminEmail)) {
            throw new IllegalStateException("ADMIN_EMAIL is already assigned to another account");
        }
        Account admin = new Account();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setEmailVerified(true);
        admin.setRole("ADMIN");
        return admin;
    }
}

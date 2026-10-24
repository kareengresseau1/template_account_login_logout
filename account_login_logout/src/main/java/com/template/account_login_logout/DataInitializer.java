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
    private final String demoAccountUsername;
    private final String demoAccountEmail;
    private final String demoAccountPassword;

    public DataInitializer(AccountRepository accountRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.username:}") String adminUsername,
                           @Value("${app.admin.email:}") String adminEmail,
                           @Value("${app.admin.password:}") String adminPassword,
                           @Value("${app.demo-seed.enabled:true}") boolean seedDemoAccount,
                           @Value("${app.demo-account.username:}") String demoAccountUsername,
                           @Value("${app.demo-account.email:}") String demoAccountEmail,
                           @Value("${app.demo-account.password:}") String demoAccountPassword) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.seedDemoAccount = seedDemoAccount;
        this.demoAccountUsername = demoAccountUsername;
        this.demoAccountEmail = demoAccountEmail;
        this.demoAccountPassword = demoAccountPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        refreshTokenRepository.deleteBySessionKeyIsNull();

        if (seedDemoAccount) {
            createDemoAccountIfMissing("jane", "jane@example.com", "password123");
            createDemoAccountIfMissing("alex", "alex@example.com", "password123"); 
            createDemoAccountIfMissing("kareen", "krgresseau@gmail.com", "password456");
            seedConfiguredDemoAccount();
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

    private void seedConfiguredDemoAccount() {
        boolean hasAnyDemoCredential = !demoAccountUsername.isBlank()
                || !demoAccountEmail.isBlank()
                || !demoAccountPassword.isBlank();
        if (!hasAnyDemoCredential) {
            return;
        }
        if (demoAccountUsername.isBlank() || demoAccountEmail.isBlank() || demoAccountPassword.isBlank()) {
            throw new IllegalStateException(
                    "To seed a demo account, set DEMO_ACCOUNT_USERNAME, DEMO_ACCOUNT_EMAIL, and DEMO_ACCOUNT_PASSWORD");
        }
        createDemoAccountIfMissing(demoAccountUsername, demoAccountEmail, demoAccountPassword);
    }

    private void createDemoAccountIfMissing(String username, String email, String rawPassword) {
        if (accountRepository.findByUsername(username).isPresent()) {
            return;
        }
        if (accountRepository.existsByEmail(email)) {
            throw new IllegalStateException("Demo account email is already assigned to another account");
        }
        Account account = new Account();
        account.setUsername(username);
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(rawPassword));
        account.setEmailVerified(true);
        accountRepository.save(account);
    }
}

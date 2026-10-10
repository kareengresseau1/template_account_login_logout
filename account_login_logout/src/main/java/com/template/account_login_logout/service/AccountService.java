package com.template.account_login_logout.service;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    public AccountService(AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          RefreshTokenService refreshTokenService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
    }

    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    public Optional<Account> findByUsername(String username) {
        return accountRepository.findByUsername(username);
    }

    public boolean existsByUsername(String username) {
        return accountRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return accountRepository.existsByEmail(email);
    }

    @Transactional
    public Account register(String username, String email, String rawPassword) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedEmail = email == null ? "" : email.trim();

        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (accountRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists");
        }

        Account account = new Account();
        account.setUsername(normalizedUsername);
        account.setEmail(normalizedEmail);
        account.setPassword(passwordEncoder.encode(rawPassword));
        account.setEmailVerified(false);
        return accountRepository.save(account);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<Account> accountResult = accountRepository.findByEmail(email);
        if (accountResult.isEmpty()) {
            return;
        }
        Account account = accountResult.get();

        String token = UUID.randomUUID().toString();
        account.setPasswordResetToken(hashToken(token));
        account.setPasswordResetTokenExpiry(Instant.now().plus(30, ChronoUnit.MINUTES));
        accountRepository.save(account);
        emailService.sendPasswordResetEmail(account, token);
    }

    @Transactional
    public void resetPassword(String email, String token, String newPassword) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found for the supplied email"));

        if (!matchesToken(account.getPasswordResetToken(), token)) {
            throw new IllegalArgumentException("Invalid password reset token");
        }
        if (account.getPasswordResetTokenExpiry() == null || account.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Password reset token has expired");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        account.setPasswordResetToken(null);
        account.setPasswordResetTokenExpiry(null);
        accountRepository.save(account);
        refreshTokenService.deleteAllForUser(account.getUsername());
    }

    @Transactional
    public void requestEmailVerification(String email) {
        Optional<Account> accountResult = accountRepository.findByEmail(email);
        if (accountResult.isEmpty()) {
            return;
        }
        Account account = accountResult.get();

        String token = UUID.randomUUID().toString();
        account.setEmailVerificationToken(hashToken(token));
        account.setEmailVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        accountRepository.save(account);
        emailService.sendEmailVerificationEmail(account, token);
    }

    @Transactional
    public void verifyEmail(String email, String token) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found for the supplied email"));

        if (!matchesToken(account.getEmailVerificationToken(), token)) {
            throw new IllegalArgumentException("Invalid email verification token");
        }
        if (account.getEmailVerificationTokenExpiry() == null || account.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Email verification token has expired");
        }

        account.setEmailVerified(true);
        account.setEmailVerificationToken(null);
        account.setEmailVerificationTokenExpiry(null);
        accountRepository.save(account);
    }

    private boolean matchesToken(String storedHash, String token) {
        if (storedHash == null || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hashToken(token).getBytes(StandardCharsets.UTF_8));
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

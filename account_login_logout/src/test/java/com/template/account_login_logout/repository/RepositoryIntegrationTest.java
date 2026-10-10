package com.template.account_login_logout.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.model.AuditLog;
import com.template.account_login_logout.model.RefreshToken;
import com.template.account_login_logout.model.RevokedToken;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void accountRepositoryFindsAccountsByUsernameAndEmail() {
        Account account = createAccount("repository-user");
        accountRepository.saveAndFlush(account);

        assertThat(accountRepository.findByUsername("repository-user")).contains(account);
        assertThat(accountRepository.findByEmail("repository-user@example.com")).contains(account);
        assertThat(accountRepository.existsByUsername("repository-user")).isTrue();
        assertThat(accountRepository.existsByEmail("repository-user@example.com")).isTrue();
    }

    @Test
    void refreshRepositoryFindsAndRevokesSessionRows() {
        Account account = accountRepository.saveAndFlush(createAccount("session-user"));
        RefreshToken first = refreshToken(account, "refresh-one", "session-one", Instant.now().minusSeconds(5));
        RefreshToken second = refreshToken(account, "refresh-two", "session-two", Instant.now());
        refreshTokenRepository.saveAndFlush(first);
        refreshTokenRepository.saveAndFlush(second);

        assertThat(refreshTokenRepository.findByToken("refresh-one")).contains(first);
        assertThat(refreshTokenRepository.findByUser_UsernameOrderByCreatedAtDesc("session-user"))
                .containsExactly(second, first);
        assertThat(refreshTokenRepository.existsBySessionKeyAndUser_Username("session-one", "session-user"))
                .isTrue();

        refreshTokenRepository.deleteBySessionKeyAndUser_Username("session-one", "session-user");

        assertThat(refreshTokenRepository.existsBySessionKeyAndUser_Username("session-one", "session-user"))
                .isFalse();
        assertThat(refreshTokenRepository.existsBySessionKeyAndUser_Username("session-two", "session-user"))
                .isTrue();
    }

    @Test
    void refreshRepositoryCanPurgeRowsWithoutSessionIds() {
        Account account = accountRepository.saveAndFlush(createAccount("legacy-user"));
        RefreshToken legacy = refreshToken(account, "legacy-refresh", null, Instant.now());
        RefreshToken current = refreshToken(account, "current-refresh", "current-session", Instant.now());
        refreshTokenRepository.saveAndFlush(legacy);
        refreshTokenRepository.saveAndFlush(current);

        refreshTokenRepository.deleteBySessionKeyIsNull();

        assertThat(refreshTokenRepository.findByToken("legacy-refresh")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("current-refresh")).isPresent();
    }

    @Test
    void revokedTokenRepositoryChecksExpiryAndDeletesExpiredRows() {
        RevokedToken expired = revokedToken("expired-hash", Instant.now().minusSeconds(5));
        RevokedToken active = revokedToken("active-hash", Instant.now().plusSeconds(60));
        revokedTokenRepository.saveAndFlush(expired);
        revokedTokenRepository.saveAndFlush(active);

        assertThat(revokedTokenRepository.existsByTokenHashAndExpiresAtAfter("active-hash", Instant.now()))
                .isTrue();
        assertThat(revokedTokenRepository.existsByTokenHashAndExpiresAtAfter("expired-hash", Instant.now()))
                .isFalse();

        revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());

        assertThat(revokedTokenRepository.findByTokenHash("expired-hash")).isEmpty();
        assertThat(revokedTokenRepository.findByTokenHash("active-hash")).isPresent();
    }

    @Test
    void auditRepositoryPersistsAuditEvents() {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventType("LOGIN_SUCCESS");
        auditLog.setUsername("audit-user");
        auditLog.setCreatedAt(Instant.now());

        AuditLog saved = auditLogRepository.saveAndFlush(auditLog);

        assertThat(auditLogRepository.findById(saved.getId())).get()
                .extracting(AuditLog::getEventType, AuditLog::getUsername)
                .containsExactly("LOGIN_SUCCESS", "audit-user");
    }

    private Account createAccount(String username) {
        Account account = new Account();
        account.setUsername(username);
        account.setEmail(username + "@example.com");
        account.setPassword("encoded-password");
        return account;
    }

    private RefreshToken refreshToken(Account account, String token, String sessionKey, Instant createdAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(account);
        refreshToken.setToken(token);
        refreshToken.setSessionKey(sessionKey);
        refreshToken.setCreatedAt(createdAt);
        refreshToken.setLastUsedAt(createdAt);
        refreshToken.setExpiryDate(createdAt.plusSeconds(3600));
        return refreshToken;
    }

    private RevokedToken revokedToken(String tokenHash, Instant expiresAt) {
        RevokedToken token = new RevokedToken();
        token.setTokenHash(tokenHash);
        token.setRevokedAt(Instant.now());
        token.setExpiresAt(expiresAt);
        token.setReason("TEST");
        return token;
    }
}

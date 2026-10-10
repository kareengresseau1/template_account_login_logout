package com.template.account_login_logout.service;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.model.RefreshToken;
import com.template.account_login_logout.repository.AccountRepository;
import com.template.account_login_logout.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final long refreshTokenExpirationMillis;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AccountRepository accountRepository,
            @Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMillis) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountRepository = accountRepository;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    @Transactional
    public RefreshToken createRefreshToken(String username, String token) {
        return createRefreshToken(username, token, UUID.randomUUID().toString(), null, null);
    }

    @Transactional
    public RefreshToken createRefreshToken(String username, String token, String ipAddress, String userAgent) {
        return createRefreshToken(username, token, UUID.randomUUID().toString(), ipAddress, userAgent);
    }

    @Transactional
    public RefreshToken createRefreshToken(String username, String token, String sessionKey,
                                           String ipAddress, String userAgent) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new TokenRefreshException("Account not found"));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(account);
        refreshToken.setToken(token);
        refreshToken.setSessionKey(sessionKey);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMillis));
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setLastUsedAt(Instant.now());
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        return refreshTokenRepository.save(refreshToken);
    }

    public List<RefreshToken> findSessions(String username) {
        return refreshTokenRepository.findByUser_UsernameOrderByCreatedAtDesc(username);
    }

    public boolean isSessionActive(String sessionKey, String username) {
        return sessionKey != null && refreshTokenRepository.existsBySessionKeyAndUser_Username(sessionKey, username);
    }

    @Transactional
    public void revokeSession(Long sessionId, String username) {
        refreshTokenRepository.deleteByIdAndUser_Username(sessionId, username);
    }

    @Transactional
    public void revokeSession(String sessionKey, String username) {
        if (sessionKey != null && username != null) {
            refreshTokenRepository.deleteBySessionKeyAndUser_Username(sessionKey, username);
        }
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("Refresh token has expired");
        }
        return token;
    }

    @Transactional
    public RefreshToken rotate(RefreshToken token, String newToken) {
        token.setToken(newToken);
        token.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMillis));
        token.setLastUsedAt(Instant.now());
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public void deleteAllForUser(String username) {
        refreshTokenRepository.deleteByUser_Username(username);
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshTokenRepository::delete);
    }
}

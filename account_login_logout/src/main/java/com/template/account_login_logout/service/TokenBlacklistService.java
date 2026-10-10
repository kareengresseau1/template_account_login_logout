package com.template.account_login_logout.service;

import com.template.account_login_logout.model.RevokedToken;
import com.template.account_login_logout.repository.RevokedTokenRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final long accessTokenExpirationMillis;

    public TokenBlacklistService(RevokedTokenRepository revokedTokenRepository,
                                 @Value("${app.jwt.access-token-expiration:900000}") long accessTokenExpirationMillis) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
    }

    public void blacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String tokenHash = hashToken(token);
        var existing = revokedTokenRepository.findByTokenHash(tokenHash);
        if (existing.isPresent()) {
            if (existing.get().getExpiresAt().isAfter(Instant.now())) {
                return;
            }
            revokedTokenRepository.delete(existing.get());
        }

        RevokedToken revokedToken = new RevokedToken();
        revokedToken.setTokenHash(tokenHash);
        revokedToken.setRevokedAt(Instant.now());
        revokedToken.setReason("LOGOUT");
        revokedToken.setExpiresAt(Instant.now().plusMillis(accessTokenExpirationMillis));
        revokedTokenRepository.save(revokedToken);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return revokedTokenRepository.existsByTokenHashAndExpiresAtAfter(hashToken(token), Instant.now());
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredTokens() {
        revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

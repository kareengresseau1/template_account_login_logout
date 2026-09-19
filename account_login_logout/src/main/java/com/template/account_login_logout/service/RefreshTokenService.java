package com.template.account_login_logout.service;

import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.model.RefreshToken;
import com.template.account_login_logout.repository.AccountRepository;
import com.template.account_login_logout.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
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
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new TokenRefreshException("Account not found"));
        refreshTokenRepository.deleteByUser_Username(username);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(account);
        refreshToken.setToken(token);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMillis));
        return refreshTokenRepository.save(refreshToken);
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

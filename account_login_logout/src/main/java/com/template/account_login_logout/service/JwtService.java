package com.template.account_login_logout.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration:900000}") long accessTokenExpirationMillis,
            @Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    public String generateAccessToken(UserDetails user) {
        return generateToken(user.getUsername(), accessTokenExpirationMillis, "access");
    }

    public String generateRefreshToken(UserDetails user) {
        return generateToken(user.getUsername(), refreshTokenExpirationMillis, "refresh");
    }

    public long getAccessTokenTtlSeconds() {
        return Duration.ofMillis(accessTokenExpirationMillis).toSeconds();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return parseClaims(token).getExpiration().after(new Date());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public long getRefreshTokenTtlMillis() {
        return refreshTokenExpirationMillis;
    }

    private String generateToken(String username, long expirationMillis, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("type", tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

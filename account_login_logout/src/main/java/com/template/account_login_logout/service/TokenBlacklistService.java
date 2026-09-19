package com.template.account_login_logout.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    public void blacklist(String token) {
        blacklistedTokens.put(token, Instant.now());
    }

    public boolean isBlacklisted(String token) {
        return blacklistedTokens.containsKey(token);
    }
}

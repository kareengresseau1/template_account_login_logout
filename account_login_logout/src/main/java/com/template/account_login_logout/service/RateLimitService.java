package com.template.account_login_logout.service;

import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); " +
                    "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; " +
                    "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean redisRequired;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public RateLimitService(StringRedisTemplate redisTemplate,
                            @Value("${app.security.rate-limit.redis-required:false}") boolean redisRequired) {
        this.redisTemplate = redisTemplate;
        this.redisRequired = redisRequired;
    }

    public boolean allow(String key, int limit, Duration window) {
        if (redisTemplate != null) {
            try {
                long windowMillis = window.toMillis();
                long bucket = Instant.now().toEpochMilli() / windowMillis;
                String redisKey = "rate-limit:" + hashKey(key) + ":" + bucket;
                Long count = redisTemplate.execute(
                        INCREMENT_WITH_EXPIRY,
                        java.util.List.of(redisKey),
                        Long.toString(windowMillis));
                return count != null && count <= limit;
            } catch (Exception ex) {
                if (redisRequired) {
                    return false;
                }
            }
        }

        if (redisRequired) {
            return false;
        }
        return allowLocally(key, limit, window);
    }

    private String hashKey(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private boolean allowLocally(String key, int limit, Duration window) {
        Deque<Long> requestTimes = attempts.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        long now = Instant.now().toEpochMilli();

        while (!requestTimes.isEmpty() && now - requestTimes.peekFirst() > window.toMillis()) {
            requestTimes.pollFirst();
        }

        if (requestTimes.size() >= limit) {
            return false;
        }

        requestTimes.addLast(now);
        return true;
    }
}

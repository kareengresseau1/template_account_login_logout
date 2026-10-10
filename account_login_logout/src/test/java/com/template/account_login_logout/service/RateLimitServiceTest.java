package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    @Test
    void localFallbackAllowsRequestsUpToTheLimitAndRejectsTheNext() {
        RateLimitService service = new RateLimitService(null, false);

        assertThat(service.allow("login-key", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(service.allow("login-key", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(service.allow("login-key", 2, Duration.ofMinutes(1))).isFalse();
        assertThat(service.allow("another-key", 2, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void requiredRedisFailsClosedWhenNoRedisTemplateIsAvailable() {
        RateLimitService service = new RateLimitService(null, true);

        assertThat(service.allow("login-key", 5, Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void redisCounterAllowsRequestsUntilTheConfiguredLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L, 2L, 3L);
        RateLimitService service = new RateLimitService(redisTemplate, true);

        assertThat(service.allow("login-key", 2, Duration.ofMinutes(5))).isTrue();
        assertThat(service.allow("login-key", 2, Duration.ofMinutes(5))).isTrue();
        assertThat(service.allow("login-key", 2, Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void redisErrorsFallbackLocallyOrFailClosedAccordingToConfiguration() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        RateLimitService fallbackService = new RateLimitService(redisTemplate, false);
        RateLimitService requiredService = new RateLimitService(redisTemplate, true);

        assertThat(fallbackService.allow("fallback-key", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(requiredService.allow("required-key", 1, Duration.ofMinutes(1))).isFalse();
    }
}

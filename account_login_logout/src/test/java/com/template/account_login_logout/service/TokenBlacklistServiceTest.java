package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenBlacklistServiceTest {

    private final TokenBlacklistService service = new TokenBlacklistService();

    @Test
    void tokenIsNotBlacklistedUntilItIsRevoked() {
        assertThat(service.isBlacklisted("access-token")).isFalse();

        service.blacklist("access-token");

        assertThat(service.isBlacklisted("access-token")).isTrue();
    }
}

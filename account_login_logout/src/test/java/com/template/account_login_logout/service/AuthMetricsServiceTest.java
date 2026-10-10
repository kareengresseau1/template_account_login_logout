package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuthMetricsServiceTest {

    @Test
    void recordsAuthenticationCountersAndLoginDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthMetricsService service = new AuthMetricsService(registry);

        service.recordLoginSuccess();
        service.recordLoginFailure();
        service.recordRefreshSuccess();
        service.recordLogout();
        var loginSample = service.startLoginTimer();
        service.stopLoginTimer(loginSample);

        assertThat(registry.get("auth.login.success").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth.login.failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth.refresh.success").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth.logout").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth.login.duration").timer().count()).isEqualTo(1);
    }
}

package com.template.account_login_logout.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class AuthMetricsService {

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter refreshSuccessCounter;
    private final Counter logoutCounter;
    private final Timer loginTimer;

    public AuthMetricsService(MeterRegistry meterRegistry) {
        this.loginSuccessCounter = Counter.builder("auth.login.success").description("Successful logins").register(meterRegistry);
        this.loginFailureCounter = Counter.builder("auth.login.failure").description("Failed login attempts").register(meterRegistry);
        this.refreshSuccessCounter = Counter.builder("auth.refresh.success").description("Successful token refreshes").register(meterRegistry);
        this.logoutCounter = Counter.builder("auth.logout").description("Logout events").register(meterRegistry);
        this.loginTimer = Timer.builder("auth.login.duration").description("Login request latency").register(meterRegistry);
    }

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginFailure() {
        loginFailureCounter.increment();
    }

    public void recordRefreshSuccess() {
        refreshSuccessCounter.increment();
    }

    public void recordLogout() {
        logoutCounter.increment();
    }

    public Timer.Sample startLoginTimer() {
        return Timer.start();
    }

    public void stopLoginTimer(Timer.Sample sample) {
        sample.stop(loginTimer);
    }
}

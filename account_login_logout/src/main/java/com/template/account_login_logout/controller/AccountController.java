package com.template.account_login_logout.controller;

import com.template.account_login_logout.authentication.dto.AuthResponse;
import com.template.account_login_logout.authentication.dto.EmailVerificationConfirmRequest;
import com.template.account_login_logout.authentication.dto.EmailVerificationRequest;
import com.template.account_login_logout.authentication.dto.LoginRequest;
import com.template.account_login_logout.authentication.dto.LogoutRequest;
import com.template.account_login_logout.authentication.dto.PasswordResetConfirmRequest;
import com.template.account_login_logout.authentication.dto.PasswordResetRequest;
import com.template.account_login_logout.authentication.dto.RefreshTokenRequest;
import com.template.account_login_logout.authentication.dto.RegisterRequest;
import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.service.AccountService;
import com.template.account_login_logout.service.AuthMetricsService;
import com.template.account_login_logout.service.AuditLogService;
import com.template.account_login_logout.service.JwtService;
import com.template.account_login_logout.service.RateLimitService;
import com.template.account_login_logout.service.RefreshTokenService;
import com.template.account_login_logout.service.TokenBlacklistService;
import com.template.account_login_logout.service.TokenRefreshException;
import com.template.account_login_logout.service.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AccountController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsService userDetailsService;
    private final AccountService accountService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    private final AuthMetricsService authMetricsService;

    public AccountController(AuthenticationManager authenticationManager,
                             JwtService jwtService,
                             RefreshTokenService refreshTokenService,
                             TokenBlacklistService tokenBlacklistService,
                             UserDetailsService userDetailsService,
                             AccountService accountService,
                             RateLimitService rateLimitService,
                             AuditLogService auditLogService,
                             AuthMetricsService authMetricsService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userDetailsService = userDetailsService;
        this.accountService = accountService;
        this.rateLimitService = rateLimitService;
        this.auditLogService = auditLogService;
        this.authMetricsService = authMetricsService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Account account = accountService.register(request.getUsername(), request.getEmail(), request.getPassword());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Account created successfully");
        body.put("username", account.getUsername());
        body.put("email", account.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    public ResponseEntity<?> register(String username, String email, String password) {
        Account account = accountService.register(username, email, password);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Account created successfully");
        body.put("username", account.getUsername());
        body.put("email", account.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        String clientKey = request.getLoginId() + "|" + httpRequest.getRemoteAddr();
        var timerSample = authMetricsService.startLoginTimer();
        if (!rateLimitService.allow(clientKey, 5, Duration.ofMinutes(5))) {
            auditLogService.audit("LOGIN_RATE_LIMITED", request.getLoginId(), httpRequest.getRemoteAddr(), "Too many login attempts");
            authMetricsService.recordLoginFailure();
            authMetricsService.stopLoginTimer(timerSample);
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later.");
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getLoginId(), request.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(auth);

            UserDetails user = (UserDetails) auth.getPrincipal();
            String sessionKey = UUID.randomUUID().toString();
            String accessToken = jwtService.generateAccessToken(user, sessionKey);
            String refreshToken = jwtService.generateRefreshToken(user, sessionKey);

            refreshTokenService.createRefreshToken(user.getUsername(), refreshToken, sessionKey,
                    httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
            auditLogService.audit("LOGIN_SUCCESS", user.getUsername(), httpRequest.getRemoteAddr(), "User logged in");
            authMetricsService.recordLoginSuccess();
            authMetricsService.stopLoginTimer(timerSample);

            return ResponseEntity.ok(new AuthResponse(
                    accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds()));
        } catch (BadCredentialsException ex) {
            auditLogService.audit("LOGIN_FAILED", request.getLoginId(), httpRequest.getRemoteAddr(), "Bad credentials");
            authMetricsService.recordLoginFailure();
            authMetricsService.stopLoginTimer(timerSample);
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(rt -> {
                    String username = rt.getUser().getUsername();
                    UserDetails user = userDetailsService.loadUserByUsername(username);
                    if (!user.isEnabled() || !user.isAccountNonLocked()) {
                        throw new BadCredentialsException("Account is disabled");
                    }
                    String newAccess = jwtService.generateAccessToken(user, rt.getSessionKey());
                    String newRefresh = jwtService.generateRefreshToken(user, rt.getSessionKey());
                    refreshTokenService.rotate(rt, newRefresh); // rotation
                    authMetricsService.recordRefreshSuccess();
                    return ResponseEntity.ok(new AuthResponse(
                            newAccess, newRefresh, jwtService.getAccessTokenTtlSeconds()));
                })
                .orElseThrow(() -> new TokenRefreshException("Refresh token is not in database"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody(required = false) LogoutRequest body,
            HttpServletRequest httpRequest) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            String username = "unknown";
            if (jwtService.isTokenValid(accessToken)) {
                username = jwtService.extractUsername(accessToken);
                tokenBlacklistService.blacklist(accessToken);
                refreshTokenService.revokeSession(jwtService.extractSessionKey(accessToken), username);
            }
            authMetricsService.recordLogout();
            auditLogService.audit("LOGOUT", username, httpRequest.getRemoteAddr(), "Current session logout requested");
        } else if (body != null) {
            if (body.getRefreshToken() != null) {
                refreshTokenService.deleteByToken(body.getRefreshToken());
            }
            String username = "unknown";
            if (body.getAccessToken() != null && jwtService.isTokenValid(body.getAccessToken())) {
                tokenBlacklistService.blacklist(body.getAccessToken());
                username = jwtService.extractUsername(body.getAccessToken());
                refreshTokenService.revokeSession(jwtService.extractSessionKey(body.getAccessToken()), username);
            }
            authMetricsService.recordLogout();
            auditLogService.audit("LOGOUT", username, httpRequest.getRemoteAddr(), "Logout request processed via body tokens");
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> sessions(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(refreshTokenService.findSessions(user.getUsername()).stream().map(session -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", session.getId());
            result.put("createdAt", session.getCreatedAt());
            result.put("lastUsedAt", session.getLastUsedAt());
            result.put("expiresAt", session.getExpiryDate());
            result.put("ipAddress", session.getIpAddress());
            result.put("userAgent", session.getUserAgent());
            return result;
        }).toList());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails user) {
        refreshTokenService.revokeSession(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                   HttpServletRequest httpRequest) {
        enforceRecoveryRateLimit("password-reset", request.getEmail(), httpRequest);
        accountService.requestPasswordReset(request.getEmail());
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "If the account exists, password reset instructions will be sent.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        accountService.resetPassword(request.getEmail(), request.getToken(), request.getNewPassword());
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Password reset successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<?> requestEmailVerification(@Valid @RequestBody EmailVerificationRequest request,
                                                       HttpServletRequest httpRequest) {
        enforceRecoveryRateLimit("email-verification", request.getEmail(), httpRequest);
        accountService.requestEmailVerification(request.getEmail());
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "If the account exists, verification instructions will be sent.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<?> confirmEmailVerification(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        accountService.verifyEmail(request.getEmail(), request.getToken());
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Email verified successfully");
        return ResponseEntity.ok(response);
    }

    private void enforceRecoveryRateLimit(String action, String email, HttpServletRequest request) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String key = action + ":" + normalizedEmail + "|" + request.getRemoteAddr();
        if (!rateLimitService.allow(key, 3, Duration.ofMinutes(15))) {
            auditLogService.audit(action.toUpperCase(Locale.ROOT) + "_RATE_LIMITED",
                    normalizedEmail, request.getRemoteAddr(), "Recovery request rate limit exceeded");
            throw new TooManyRequestsException("Too many requests. Please try again later.");
        }
    }

}

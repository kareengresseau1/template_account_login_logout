package com.template.account_login_logout.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.authentication.dto.AuthResponse;
import com.template.account_login_logout.authentication.dto.LoginRequest;
import com.template.account_login_logout.authentication.dto.PasswordResetRequest;
import com.template.account_login_logout.authentication.dto.RefreshTokenRequest;
import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.model.RefreshToken;
import com.template.account_login_logout.service.AccountService;
import com.template.account_login_logout.service.AuthMetricsService;
import com.template.account_login_logout.service.AuditLogService;
import com.template.account_login_logout.service.JwtService;
import com.template.account_login_logout.service.RateLimitService;
import com.template.account_login_logout.service.RefreshTokenService;
import com.template.account_login_logout.service.TokenBlacklistService;
import com.template.account_login_logout.service.TooManyRequestsException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private AccountService accountService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuthMetricsService authMetricsService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AccountController controller;

    @Test
    void loginAuthenticatesUserAndReturnsTokens() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("jane");
        request.setPassword("password");
        UserDetails user = user("jane");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        when(rateLimitService.allow("jane|127.0.0.1", 5, java.time.Duration.ofMinutes(5))).thenReturn(true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(jwtService.generateAccessToken(eq(user), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(user), anyString())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<AuthResponse> response = controller.login(request, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getBody().getExpiresIn()).isEqualTo(900L);
        verify(refreshTokenService).createRefreshToken(
            eq("jane"), eq("refresh-token"), anyString(), eq("127.0.0.1"), isNull());
    }

    @Test
    void refreshRotatesStoredTokenAndReturnsNewPair() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");
        Account account = new Account();
        account.setUsername("jane");
        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(account);
        storedToken.setToken("old-refresh-token");
        storedToken.setSessionKey("session-1");
        storedToken.setExpiryDate(Instant.now().plusSeconds(60));
        UserDetails user = user("jane");
        when(refreshTokenService.findByToken("old-refresh-token")).thenReturn(Optional.of(storedToken));
        when(refreshTokenService.verifyExpiration(storedToken)).thenReturn(storedToken);
        when(userDetailsService.loadUserByUsername("jane")).thenReturn(user);
        when(jwtService.generateAccessToken(user, "session-1")).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user, "session-1")).thenReturn("new-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<AuthResponse> response = controller.refresh(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenService).rotate(storedToken, "new-refresh-token");
    }

    @Test
    void registerCreatesAccountAndReturnsCreatedResponse() {
        Account account = new Account();
        account.setUsername("jane");
        account.setEmail("jane@example.com");
        account.setPassword("encoded-password");
        when(accountService.register("jane", "jane@example.com", "secret123")).thenReturn(account);

        ResponseEntity<?> response = controller.register("jane", "jane@example.com", "secret123");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        verify(accountService).register("jane", "jane@example.com", "secret123");
    }

    @Test
    void logoutRevokesOnlyTheCurrentAccessTokenSession() {
        when(jwtService.isTokenValid("access-token")).thenReturn(true);
        when(jwtService.extractUsername("access-token")).thenReturn("jane");
        when(jwtService.extractSessionKey("access-token")).thenReturn("session-1");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.logout("Bearer access-token", null, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Logged out");
        verify(tokenBlacklistService).blacklist("access-token");
        verify(refreshTokenService).revokeSession("session-1", "jane");
        verify(refreshTokenService, never()).deleteAllForUser("jane");
    }

    @Test
    void passwordResetRequestsAreRateLimitedByEmailAndAddress() {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail("jane@example.com");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        when(rateLimitService.allow("password-reset:jane@example.com|127.0.0.1", 3,
                java.time.Duration.ofMinutes(15))).thenReturn(false);

        assertThatThrownBy(() -> controller.requestPasswordReset(request, httpRequest))
                .isInstanceOf(TooManyRequestsException.class);

        verify(accountService, never()).requestPasswordReset("jane@example.com");
    }

    private UserDetails user(String username) {
        return User.withUsername(username)
                .password("encoded-password")
                .authorities("ROLE_USER")
                .build();
    }
}

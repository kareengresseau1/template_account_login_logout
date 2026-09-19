package com.template.account_login_logout.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.authentication.dto.AuthResponse;
import com.template.account_login_logout.authentication.dto.LoginRequest;
import com.template.account_login_logout.authentication.dto.RefreshTokenRequest;
import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.model.RefreshToken;
import com.template.account_login_logout.service.JwtService;
import com.template.account_login_logout.service.RefreshTokenService;
import com.template.account_login_logout.service.TokenBlacklistService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
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
    private Authentication authentication;

    @InjectMocks
    private AccountController controller;

    @Test
    void loginAuthenticatesUserAndReturnsTokens() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("jane");
        request.setPassword("password");
        UserDetails user = user("jane");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<AuthResponse> response = controller.login(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getBody().getExpiresIn()).isEqualTo(900L);
        verify(refreshTokenService).createRefreshToken("jane", "refresh-token");
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
        storedToken.setExpiryDate(Instant.now().plusSeconds(60));
        UserDetails user = user("jane");
        when(refreshTokenService.findByToken("old-refresh-token")).thenReturn(Optional.of(storedToken));
        when(refreshTokenService.verifyExpiration(storedToken)).thenReturn(storedToken);
        when(userDetailsService.loadUserByUsername("jane")).thenReturn(user);
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<AuthResponse> response = controller.refresh(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenService).rotate(storedToken, "new-refresh-token");
    }

    @Test
    void logoutRevokesAccessTokenAndDeletesUserRefreshToken() {
        when(jwtService.extractUsername("access-token")).thenReturn("jane");

        ResponseEntity<?> response = controller.logout("Bearer access-token", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("Logged out");
        verify(tokenBlacklistService).blacklist("access-token");
        verify(refreshTokenService).deleteAllForUser("jane");
    }

    private UserDetails user(String username) {
        return User.withUsername(username)
                .password("encoded-password")
                .authorities("ROLE_USER")
                .build();
    }
}

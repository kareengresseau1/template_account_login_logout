package com.template.account_login_logout.controller;

import com.template.account_login_logout.authentication.dto.AuthResponse;
import com.template.account_login_logout.authentication.dto.LoginRequest;
import com.template.account_login_logout.authentication.dto.LogoutRequest;
import com.template.account_login_logout.authentication.dto.RefreshTokenRequest;
import com.template.account_login_logout.service.JwtService;
import com.template.account_login_logout.service.RefreshTokenService;
import com.template.account_login_logout.service.TokenBlacklistService;
import com.template.account_login_logout.service.TokenRefreshException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AccountController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsService userDetailsService;

    public AccountController(AuthenticationManager authenticationManager,
                             JwtService jwtService,
                             RefreshTokenService refreshTokenService,
                             TokenBlacklistService tokenBlacklistService,
                             UserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLoginId(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserDetails user = (UserDetails) auth.getPrincipal();
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenService.createRefreshToken(user.getUsername(), refreshToken);

        return ResponseEntity.ok(new AuthResponse(
                accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(rt -> {
                    String username = rt.getUser().getUsername();
                    UserDetails user = userDetailsService.loadUserByUsername(username);
                    String newAccess = jwtService.generateAccessToken(user);
                    String newRefresh = jwtService.generateRefreshToken(user);
                    refreshTokenService.rotate(rt, newRefresh); // rotation
                    return ResponseEntity.ok(new AuthResponse(
                            newAccess, newRefresh, jwtService.getAccessTokenTtlSeconds()));
                })
                .orElseThrow(() -> new TokenRefreshException("Refresh token is not in database"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody(required = false) LogoutRequest body) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            tokenBlacklistService.blacklist(accessToken);
            String username = jwtService.extractUsername(accessToken);
            refreshTokenService.deleteAllForUser(username);
        } else if (body != null) {
            if (body.getRefreshToken() != null) {
                refreshTokenService.deleteByToken(body.getRefreshToken());
            }
            if (body.getAccessToken() != null) {
                tokenBlacklistService.blacklist(body.getAccessToken());
            }
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

}

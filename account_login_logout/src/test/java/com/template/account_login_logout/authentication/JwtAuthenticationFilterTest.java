package com.template.account_login_logout.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.security.CustomUserDetailsService;
import com.template.account_login_logout.service.JwtService;
import com.template.account_login_logout.service.RefreshTokenService;
import com.template.account_login_logout.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsBlacklistedTokenBeforeContinuingChain() throws Exception {
        TestableJwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenBlacklistService.isBlacklisted("revoked-token")).thenReturn(true);

        filter.runFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(jwtService, never()).isTokenValid("revoked-token");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void ignoresInvalidTokenAndContinuesAsAnonymous() throws Exception {
        TestableJwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenBlacklistService.isBlacklisted("invalid-token")).thenReturn(false);
        when(jwtService.isTokenValid("invalid-token")).thenReturn(false);

        filter.runFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatesValidTokenWhenItsSessionAndAccountAreActive() throws Exception {
        TestableJwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails user = User.withUsername("sam").password("hash").authorities("ROLE_USER").build();
        when(tokenBlacklistService.isBlacklisted("valid-token")).thenReturn(false);
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractUsername("valid-token")).thenReturn("sam");
        when(jwtService.extractSessionKey("valid-token")).thenReturn("session-1");
        when(refreshTokenService.isSessionActive("session-1", "sam")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("sam")).thenReturn(user);

        filter.runFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("sam");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsValidTokenWhenItsSessionWasRevoked() throws Exception {
        TestableJwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenBlacklistService.isBlacklisted("valid-token")).thenReturn(false);
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractUsername("valid-token")).thenReturn("sam");
        when(jwtService.extractSessionKey("valid-token")).thenReturn("session-1");
        when(refreshTokenService.isSessionActive("session-1", "sam")).thenReturn(false);

        filter.runFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(userDetailsService, never()).loadUserByUsername("sam");
        verify(filterChain, never()).doFilter(request, response);
    }

    private TestableJwtAuthenticationFilter filter() {
        return new TestableJwtAuthenticationFilter();
    }

    private class TestableJwtAuthenticationFilter extends JwtAuthenticationFilter {
        private TestableJwtAuthenticationFilter() {
            super(jwtService, tokenBlacklistService, refreshTokenService, userDetailsService);
        }

        private void runFilter(MockHttpServletRequest request,
                               MockHttpServletResponse response,
                               FilterChain chain) throws Exception {
            doFilterInternal(request, response, chain);
        }
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}

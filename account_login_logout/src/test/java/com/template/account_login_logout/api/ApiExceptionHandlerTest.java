package com.template.account_login_logout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.service.TooManyRequestsException;
import com.template.account_login_logout.service.TokenRefreshException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void badCredentialsReturnGenericUnauthorizedMessage() {
        var response = handler.handleBadCredentials(new BadCredentialsException("account detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError()).isEqualTo("AUTHENTICATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void rateLimitErrorsReturnTooManyRequests() {
        var response = handler.handleTooManyRequests(new TooManyRequestsException("Slow down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getError()).isEqualTo("RATE_LIMIT_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Slow down");
    }

    @Test
    void validationViolationsReturnBadRequest() {
        var response = handler.handleConstraintViolation(
                new ConstraintViolationException("invalid email", java.util.Set.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("invalid email");
    }

    @Test
    void fieldValidationErrorsAreJoinedIntoBadRequestMessage() {
        BindingResult bindingResult = mock(BindingResult.class);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("request", "email", "Email is required"),
                new FieldError("request", "password", "Password is required")));

        var response = handler.handleMethodArgumentNotValid(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Email is required, Password is required");
    }

    @Test
    void refreshFailuresReturnUnauthorized() {
        var response = handler.handleTokenRefreshException(new TokenRefreshException("Refresh expired"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError()).isEqualTo("TOKEN_REFRESH_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Refresh expired");
    }

    @Test
    void illegalRequestsReturnBadRequest() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("Invalid role"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid role");
    }

    @Test
    void unexpectedExceptionsHideInternalDetails() {
        var response = handler.handleGenericException(new IllegalStateException("database credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("SERVER_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("database credentials");
        assertThat(response.getBody().getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }
}

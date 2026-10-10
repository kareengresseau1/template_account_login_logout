package com.template.account_login_logout.authentication.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.template.account_login_logout.authentication.dto.EmailVerificationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthenticationDtoValidationTest {

    private static Validator validator;
    private static AutoCloseable validatorFactory;

    @BeforeAll
    static void createValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        validatorFactory = factory::close;
    }

    @AfterAll
    static void closeValidator() throws Exception {
        validatorFactory.close();
    }

    @Test
    void loginAndRefreshRequireNonBlankCredentials() {
        LoginRequest login = new LoginRequest();
        RefreshTokenRequest refresh = new RefreshTokenRequest();

        assertThat(validator.validate(login)).extracting("message")
                .contains("Login ID is required", "Password is required");
        assertThat(validator.validate(refresh)).extracting("message")
                .containsExactly("Refresh token is required");
    }

    @Test
    void registrationRequiresUsernameValidEmailAndPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("sam");
        request.setEmail("not-an-email");
        request.setPassword(" ");

        assertThat(validator.validate(request)).extracting("message")
                .contains("Email should be valid", "Password is required");
    }

    @Test
    void recoveryRequestsRequireValidEmailAndConfirmationTokens() {
        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setEmail("invalid");
        EmailVerificationRequest verificationRequest = new EmailVerificationRequest();
        verificationRequest.setEmail("invalid");
        PasswordResetConfirmRequest resetConfirm = new PasswordResetConfirmRequest();
        resetConfirm.setEmail("sam@example.com");
        EmailVerificationConfirmRequest verifyConfirm = new EmailVerificationConfirmRequest();
        verifyConfirm.setEmail("sam@example.com");

        assertThat(validator.validate(resetRequest)).extracting("message")
                .contains("Email should be valid");
        assertThat(validator.validate(verificationRequest)).extracting("message")
            .contains("Email should be valid");
        assertThat(validator.validate(resetConfirm)).extracting("message")
                .contains("Token is required", "New password is required");
        assertThat(validator.validate(verifyConfirm)).extracting("message")
                .contains("Token is required");
    }

    @Test
    void logoutTokensAreOptional() {
        assertThat(validator.validate(new LogoutRequest())).isEmpty();
    }
}

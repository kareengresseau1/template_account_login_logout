package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.template.account_login_logout.model.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsPasswordResetEmailWithConfiguredSenderAndToken() {
        Account account = account();
        EmailService service = new EmailService(mailSender, "auth@example.com");

        service.sendPasswordResetEmail(account, "reset-token");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("auth@example.com");
        assertThat(message.getValue().getTo()).containsExactly("sam@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Password reset request");
        assertThat(message.getValue().getText()).contains("reset-token", "30 minutes");
    }

    @Test
    void sendsVerificationEmailWithTokenAndExpiry() {
        EmailService service = new EmailService(mailSender, "auth@example.com");

        service.sendEmailVerificationEmail(account(), "verify-token");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getSubject()).isEqualTo("Verify your email address");
        assertThat(message.getValue().getText()).contains("verify-token", "24 hours");
    }

    private Account account() {
        Account account = new Account();
        account.setEmail("sam@example.com");
        return account;
    }
}

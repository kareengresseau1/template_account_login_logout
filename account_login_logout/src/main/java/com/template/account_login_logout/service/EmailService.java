package com.template.account_login_logout.service;

import com.template.account_login_logout.model.Account;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPasswordResetEmail(Account account, String token) {
        sendEmail(
                account.getEmail(),
                "Password reset request",
                "Use the following token to reset your password: " + token + "\n" +
                        "This token expires in 30 minutes."
        );
    }

    public void sendEmailVerificationEmail(Account account, String token) {
        sendEmail(
                account.getEmail(),
                "Verify your email address",
                "Use the following verification token to confirm your email: " + token + "\n" +
                        "This token expires in 24 hours."
        );
    }

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(fromAddress);
        mailSender.send(message);
    }
}

package com.sprint.bank.auth.service;

import com.sprint.bank.exception.EmailDeliveryUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryService {

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String fromAddress;
    private final String publicBaseUrl;
    private final String mailHost;

    public EmailDeliveryService(
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${app.mail.from:noreply@northstarbank.local}") String fromAddress,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSenders = mailSenders;
        this.fromAddress = fromAddress;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.mailHost = mailHost;
    }

    public boolean isAvailable() {
        return mailHost != null && !mailHost.isBlank() && mailSenders.getIfAvailable() != null;
    }

    public void sendVerification(String email, String token) {
        send(email, "Verify your Northstar Bank account",
                "Verify your email address using this link: "
                        + publicBaseUrl + "/auth/verify-email#token=" + token);
    }

    public void sendPasswordReset(String email, String token) {
        send(email, "Reset your Northstar Bank password",
                "Reset your password using this link: "
                        + publicBaseUrl + "/auth/reset-password#token=" + token);
    }

    private void send(String recipient, String subject, String body) {
        JavaMailSender sender = isAvailable() ? mailSenders.getIfAvailable() : null;
        if (sender == null) {
            throw new EmailDeliveryUnavailableException();
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}

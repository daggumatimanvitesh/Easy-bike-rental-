package com.easybikerentals.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

@Service
public class VerificationEmailSender {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.frontend.base-url:http://127.0.0.1:5174}")
    private String frontendBaseUrl;

    public VerificationEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerification(String email, String name, String token) {
        if (host.isBlank() || username.isBlank() || password.isBlank() || from.isBlank()) {
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_SENDER_NOT_CONFIGURED",
                    "Email delivery is not configured. Set MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, and MAIL_FROM on the backend.");
        }

        String url = frontendBaseUrl.replaceAll("/+$", "") + "/verify-email?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String safeName = HtmlUtils.htmlEscape(name);
        String safeEmail = HtmlUtils.htmlEscape(email);
        String safeUrl = HtmlUtils.htmlEscape(url);
        String subject = "Verify Your Email Address – Easy Bike Rental";
        String plainText = "Hello " + name + ",\n\n"
                + "Thank you for registering with Easy Bike Rental. Confirm that " + email
                + " belongs to you by opening this link:\n" + url + "\n\n"
                + "Your verification link expires in 15 minutes. If you did not request this, ignore this email.\n\n"
                + "Thank you,\nEasy Bike Rental Team";
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:auto;color:#14213d\">"
                + "<h1>Easy Bike Rental</h1><p>Hello " + safeName + ",</p>"
                + "<p>Thank you for registering with Easy Bike Rental. Confirm that <strong>" + safeEmail
                + "</strong> belongs to you by clicking below.</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + safeUrl
                + "\" style=\"background:#2563eb;color:#fff;padding:14px 22px;border-radius:6px;text-decoration:none;font-weight:bold\">"
                + "Verify Email Address</a></p><p>Your account will only be created after successful email verification.</p>"
                + "<p>This verification link expires in 15 minutes.</p>"
                + "<p>If you did not request this, you can safely ignore this email.</p>"
                + "<p>Thank you,<br/>Easy Bike Rental Team</p></div>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(message);
        } catch (MailException | MessagingException ex) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED",
                    "The verification email could not be sent. Check the backend SMTP settings and try again.");
        }
    }
}

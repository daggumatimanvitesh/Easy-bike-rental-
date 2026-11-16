package com.easybikerentals.service;

import com.easybikerentals.entity.EmailVerification;
import com.easybikerentals.entity.User;
import com.easybikerentals.repository.EmailVerificationRepository;
import com.easybikerentals.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final Duration EMAIL_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_EMAIL_SENDS_PER_HOUR = 5;
    private static final int MAX_REQUESTS_PER_IP_PER_HOUR = 10;

    private final EmailVerificationRepository verifications;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final VerificationEmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, IpWindow> ipWindows = new ConcurrentHashMap<>();

    public EmailVerificationService(EmailVerificationRepository verifications, UserRepository users,
                                   PasswordEncoder passwordEncoder, VerificationEmailSender emailSender) {
        this.verifications = verifications;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    @Transactional
    public void beginRegistration(String name, String email, String password, String phone, String address, String remoteAddress) {
        enforceIpLimit(remoteAddress);
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "This email address is already registered. Please log in.");
        }
        EmailVerification pending = verifications.findByEmail(normalizedEmail).orElse(null);
        if (pending != null && pending.verifiedAt != null) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED",
                    "Your email address is already verified. Please log in.");
        }
        if (pending != null) {
            throw new AuthFlowException(HttpStatus.CONFLICT, "VERIFICATION_PENDING",
                    "A verification is already pending for this email. Resend the verification email to continue.");
        }

        pending = new EmailVerification();
        pending.email = normalizedEmail;
        pending.name = name.trim();
        pending.passwordHash = passwordEncoder.encode(password);
        pending.phone = phone.trim();
        pending.address = address == null ? null : address.trim();
        verifications.saveAndFlush(pending);
        issueToken(pending, Instant.now());
    }

    @Transactional
    public ResendResult resend(String email, String remoteAddress) {
        enforceIpLimit(remoteAddress);
        String normalizedEmail = normalizeEmail(email);
        User existing = users.findByEmail(normalizedEmail).orElse(null);
        if (existing != null && existing.emailVerified) {
            return new ResendResult(false, true,
                    "Your email address is already verified. You can log in.");
        }

        EmailVerification pending = verifications.findByEmail(normalizedEmail).orElse(null);
        if (pending == null || pending.verifiedAt != null) {
            return new ResendResult(false, false,
                    "If an account is awaiting verification at that address, a new email has been sent.");
        }
        issueToken(pending, Instant.now());
        return new ResendResult(true, false,
                "A new verification email has been sent. Check your inbox.");
    }

    @Transactional
    public VerificationResult verify(String token) {
        EmailVerification pending = verifications.findByTokenHash(hash(token)).orElseThrow(() ->
                new AuthFlowException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_INVALID",
                        "This verification link is invalid. Request a new verification email."));
        if (pending.verifiedAt != null) {
            return new VerificationResult(true,
                    "Your email address is already verified. You can log in.");
        }
        Instant now = Instant.now();
        if (pending.expiresAt == null || !pending.expiresAt.isAfter(now)) {
            throw new AuthFlowException(HttpStatus.GONE, "VERIFICATION_TOKEN_EXPIRED",
                    "Your verification link has expired. Please request a new verification email.");
        }

        if (users.existsByEmail(pending.email)) {
            pending.verifiedAt = now;
            clearPendingDetails(pending);
            verifications.save(pending);
            return new VerificationResult(true,
                    "Your email address is already verified. You can log in.");
        }

        User user = new User();
        user.name = pending.name;
        user.email = pending.email;
        user.password = pending.passwordHash;
        user.phone = pending.phone;
        user.address = pending.address;
        user.role = "CUSTOMER";
        user.active = true;
        user.emailVerified = true;
        users.saveAndFlush(user);

        pending.verifiedAt = now;
        clearPendingDetails(pending);
        verifications.save(pending);
        return new VerificationResult(false,
                "Email Verified Successfully! Your Easy Bike Rental account has been created. You can now log in.");
    }

    public boolean isPendingSignupWithPassword(String email, String password) {
        EmailVerification pending = verifications.findByEmail(normalizeEmail(email)).orElse(null);
        return pending != null && pending.verifiedAt == null && pending.passwordHash != null
                && passwordEncoder.matches(password, pending.passwordHash);
    }

    private void issueToken(EmailVerification pending, Instant now) {
        if (pending.lastSentAt != null && pending.lastSentAt.plus(EMAIL_COOLDOWN).isAfter(now)) {
            long retrySeconds = Math.max(1, Duration.between(now, pending.lastSentAt.plus(EMAIL_COOLDOWN)).toSeconds());
            throw new AuthFlowException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_RESEND_COOLDOWN",
                    "Please wait " + retrySeconds + " seconds before requesting another verification email.");
        }
        if (pending.sendWindowStartedAt == null || pending.sendWindowStartedAt.plus(SEND_WINDOW).isBefore(now)) {
            pending.sendWindowStartedAt = now;
            pending.sendsInWindow = 0;
        }
        if (pending.sendsInWindow >= MAX_EMAIL_SENDS_PER_HOUR) {
            throw new AuthFlowException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_SEND_LIMIT",
                    "Too many verification emails were requested. Please try again later.");
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        pending.tokenHash = hash(token);
        pending.expiresAt = now.plus(TOKEN_LIFETIME);
        pending.lastSentAt = now;
        pending.sendsInWindow++;
        verifications.saveAndFlush(pending);
        emailSender.sendVerification(pending.email, pending.name, token);
    }

    private void enforceIpLimit(String remoteAddress) {
        String key = (remoteAddress == null || remoteAddress.isBlank()) ? "unknown" : remoteAddress;
        Instant now = Instant.now();
        IpWindow window = ipWindows.compute(key, (ignored, current) -> {
            if (current == null || current.startedAt.plus(SEND_WINDOW).isBefore(now)) return new IpWindow(now, 1);
            return new IpWindow(current.startedAt, current.count + 1);
        });
        if (window.count > MAX_REQUESTS_PER_IP_PER_HOUR) {
            throw new AuthFlowException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_IP_LIMIT",
                    "Too many verification requests came from this connection. Please try again later.");
        }
        if (ipWindows.size() > 10000) {
            ipWindows.entrySet().removeIf(entry -> entry.getValue().startedAt.plus(SEND_WINDOW).isBefore(now));
        }
    }

    private void clearPendingDetails(EmailVerification pending) {
        pending.name = null;
        pending.passwordHash = null;
        pending.phone = null;
        pending.address = null;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record IpWindow(Instant startedAt, int count) {}
    public record ResendResult(boolean sent, boolean alreadyVerified, String message) {}
    public record VerificationResult(boolean alreadyVerified, String message) {}
}

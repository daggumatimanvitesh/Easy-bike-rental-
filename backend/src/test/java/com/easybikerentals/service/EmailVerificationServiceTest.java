package com.easybikerentals.service;

import com.easybikerentals.entity.EmailVerification;
import com.easybikerentals.entity.User;
import com.easybikerentals.repository.EmailVerificationRepository;
import com.easybikerentals.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock private EmailVerificationRepository verifications;
    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationEmailSender emailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(verifications, users, passwordEncoder, emailSender);
    }

    @Test
    void registrationSendsTokenButDoesNotCreateUser() {
        when(users.existsByEmail("rider@example.test")).thenReturn(false);
        when(verifications.findByEmail("rider@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("bcrypt-hash");
        when(verifications.saveAndFlush(any(EmailVerification.class))).thenAnswer(call -> call.getArgument(0));

        service.beginRegistration("Test Rider", "Rider@Example.Test", "StrongPassword123!", "+1 555 0100", "Test address", "127.0.0.1");

        ArgumentCaptor<EmailVerification> row = ArgumentCaptor.forClass(EmailVerification.class);
        verify(verifications, org.mockito.Mockito.atLeastOnce()).saveAndFlush(row.capture());
        EmailVerification pending = row.getValue();
        assertEquals("rider@example.test", pending.email);
        assertEquals("bcrypt-hash", pending.passwordHash);
        assertNotNull(pending.tokenHash);
        assertEquals(64, pending.tokenHash.length());
        assertNotNull(pending.expiresAt);
        assertEquals(1, pending.sendsInWindow);
        verify(emailSender).sendVerification(org.mockito.ArgumentMatchers.eq("rider@example.test"),
                org.mockito.ArgumentMatchers.eq("Test Rider"), org.mockito.ArgumentMatchers.anyString());
        verify(users, never()).save(any(User.class));
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void onlyValidUnexpiredTokenCreatesVerifiedUserAndConsumesPendingDetails() {
        EmailVerification pending = new EmailVerification();
        pending.email = "rider@example.test";
        pending.name = "Test Rider";
        pending.passwordHash = "bcrypt-hash";
        pending.phone = "+1 555 0100";
        pending.address = "Test address";
        pending.tokenHash = hash("raw-token");
        pending.expiresAt = Instant.now().plusSeconds(900);
        when(verifications.findByTokenHash(pending.tokenHash)).thenReturn(Optional.of(pending));
        when(users.existsByEmail(pending.email)).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        EmailVerificationService.VerificationResult result = service.verify("raw-token");

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(created.capture());
        assertEquals("rider@example.test", created.getValue().email);
        assertEquals("bcrypt-hash", created.getValue().password);
        assertEquals("CUSTOMER", created.getValue().role);
        assertTrue(created.getValue().active);
        assertTrue(created.getValue().emailVerified);
        assertNotNull(pending.verifiedAt);
        assertNull(pending.passwordHash);
        assertNull(pending.name);
        assertEquals("Email Verified Successfully! Your Easy Bike Rental account has been created. You can now log in.", result.message());
    }

    @Test
    void expiredTokenDoesNotCreateAnAccount() {
        EmailVerification pending = new EmailVerification();
        pending.email = "rider@example.test";
        pending.tokenHash = hash("raw-token");
        pending.expiresAt = Instant.now().minusSeconds(1);
        when(verifications.findByTokenHash(pending.tokenHash)).thenReturn(Optional.of(pending));

        AuthFlowException error = assertThrows(AuthFlowException.class, () -> service.verify("raw-token"));

        assertEquals("VERIFICATION_TOKEN_EXPIRED", error.getCode());
        assertEquals("Your verification link has expired. Please request a new verification email.", error.getMessage());
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void resendRotatesTheStoredHashAndSendsANewToken() {
        EmailVerification pending = new EmailVerification();
        pending.email = "rider@example.test";
        pending.name = "Test Rider";
        pending.passwordHash = "bcrypt-hash";
        pending.tokenHash = "old-hash";
        pending.lastSentAt = Instant.now().minusSeconds(61);
        pending.sendWindowStartedAt = Instant.now().minusSeconds(61);
        pending.sendsInWindow = 1;
        when(users.findByEmail(pending.email)).thenReturn(Optional.empty());
        when(verifications.findByEmail(pending.email)).thenReturn(Optional.of(pending));
        when(verifications.saveAndFlush(any(EmailVerification.class))).thenAnswer(call -> call.getArgument(0));

        EmailVerificationService.ResendResult result = service.resend(pending.email, "127.0.0.1");

        assertEquals("A new verification email has been sent. Check your inbox.", result.message());
        assertNotEquals("old-hash", pending.tokenHash);
        assertEquals(2, pending.sendsInWindow);
        verify(emailSender).sendVerification(org.mockito.ArgumentMatchers.eq(pending.email),
                org.mockito.ArgumentMatchers.eq("Test Rider"), org.mockito.ArgumentMatchers.anyString());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

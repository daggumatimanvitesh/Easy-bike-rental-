package com.easybikerentals.controller;

import com.easybikerentals.entity.User;
import com.easybikerentals.repository.UserRepository;
import com.easybikerentals.security.JwtService;
import com.easybikerentals.service.AuthFlowException;
import com.easybikerentals.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EmailVerificationService emailVerification;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                          EmailVerificationService emailVerification) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.emailVerification = emailVerification;
    }

    record Register(@NotBlank @Size(max = 120) String name,
                    @NotBlank @Email @Size(max = 180) String email,
                    @NotBlank @Size(max = 40) String phone,
                    @Size(max = 500) String address,
                    @NotBlank @Size(min = 8, max = 72) String password,
                    @NotBlank @Size(min = 8, max = 72) String confirmPassword) {}

    record Login(@NotBlank @Email @Size(max = 180) String email,
                 @NotBlank String password) {}

    record Verify(@NotBlank @Size(max = 128) String token) {}

    record Resend(@NotBlank @Email @Size(max = 180) String email) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Register request, HttpServletRequest servletRequest) {
        if (!request.password().equals(request.confirmPassword())) {
            return error(HttpStatus.BAD_REQUEST, "PASSWORDS_DO_NOT_MATCH", "Passwords do not match.");
        }
        try {
            emailVerification.beginRegistration(request.name(), request.email(), request.password(),
                    request.phone(), request.address(), servletRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "code", "EMAIL_VERIFICATION_REQUIRED",
                    "message", "A verification email has been sent to your registered email address. Please check your inbox and click the Verify Email button to activate your account.",
                    "email", request.email().trim()));
        } catch (AuthFlowException ex) {
            return error(ex.getStatus(), ex.getCode(), ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "This email address is already registered or has a verification request in progress.");
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody Verify request) {
        try {
            EmailVerificationService.VerificationResult result = emailVerification.verify(request.token());
            String code = result.alreadyVerified() ? "EMAIL_ALREADY_VERIFIED" : "EMAIL_VERIFIED";
            return ResponseEntity.ok(Map.of("code", code, "message", result.message()));
        } catch (AuthFlowException ex) {
            return error(ex.getStatus(), ex.getCode(), ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "This email address is already registered. Please log in.");
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody Resend request,
                                                HttpServletRequest servletRequest) {
        try {
            EmailVerificationService.ResendResult result = emailVerification.resend(
                    request.email(), servletRequest.getRemoteAddr());
            String code = result.alreadyVerified() ? "EMAIL_ALREADY_VERIFIED"
                    : result.sent() ? "VERIFICATION_EMAIL_SENT" : "VERIFICATION_REQUEST_ACCEPTED";
            return ResponseEntity.ok(Map.of("code", code, "message", result.message()));
        } catch (AuthFlowException ex) {
            return error(ex.getStatus(), ex.getCode(), ex.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email).orElse(null);
        if (user == null && emailVerification.isPendingSignupWithPassword(email, request.password())) {
            return error(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Your email address is not verified. Please check your inbox and verify your email before logging in.");
        }
        if (user == null || !user.active || !encoder.matches(request.password(), user.password)) {
            return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
        }
        if (!user.emailVerified) {
            return error(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Your email address is not verified. Please check your inbox and verify your email before logging in.");
        }
        return ResponseEntity.ok(Map.of(
                "token", jwt.create(user.email, user.role),
                "user", Map.of("id", user.id, "name", user.name, "email", user.email, "role", user.role)));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}

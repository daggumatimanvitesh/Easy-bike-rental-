package com.easybikerentals.controller;

import com.easybikerentals.config.SecurityConfig;
import com.easybikerentals.repository.UserRepository;
import com.easybikerentals.security.JwtFilter;
import com.easybikerentals.security.JwtService;
import com.easybikerentals.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = "app.cors.origin=http://127.0.0.1:5174")
@Import({SecurityConfig.class, JwtFilter.class})
class AuthPublicEndpointTest {
    @Autowired private MockMvc mvc;

    @MockBean private JwtService jwtService;
    @MockBean private UserRepository users;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private EmailVerificationService emailVerification;

    @Test
    void emailVerificationDoesNotRequireBearerToken() throws Exception {
        when(emailVerification.verify("verification-token"))
                .thenReturn(new EmailVerificationService.VerificationResult(
                        false, "Email Verified Successfully."));

        mvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"verification-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFIED"));

        verify(emailVerification).verify("verification-token");
    }

    @Test
    void registrationAndResendDoNotRequireBearerToken() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Rider\",\"email\":\"rider@example.test\","
                                + "\"phone\":\"1234567890\",\"address\":\"Test\","
                                + "\"password\":\"Password123\",\"confirmPassword\":\"Password123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));

        when(emailVerification.resend("rider@example.test", "127.0.0.1"))
                .thenReturn(new EmailVerificationService.ResendResult(
                        true, false, "Verification email sent."));

        mvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rider@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EMAIL_SENT"));
    }
}

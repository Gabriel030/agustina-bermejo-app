package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.ForgotPasswordRequest;
import com.red_escuela.backend.identity.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "auth.rate-limit.login.capacity=2",
        "auth.rate-limit.login.refill-per-minute=2",
        "auth.rate-limit.forgot-password.capacity=2",
        "auth.rate-limit.forgot-password.refill-per-minute=2"
})
class RateLimitingTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void superarLaCapacidadDeLoginDevuelve429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("no-existe", "cualquiera"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("no-existe", "cualquiera"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void superarLaCapacidadDeForgotPasswordDevuelve429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("no-existe@example.com"))))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("no-existe@example.com"))))
                .andExpect(status().isTooManyRequests());
    }
}

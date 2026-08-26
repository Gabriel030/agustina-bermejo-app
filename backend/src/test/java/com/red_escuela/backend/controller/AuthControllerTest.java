package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AuthResponse;
import com.red_escuela.backend.dto.ForgotPasswordRequest;
import com.red_escuela.backend.dto.LoginRequest;
import com.red_escuela.backend.dto.LogoutRequest;
import com.red_escuela.backend.dto.RefreshRequest;
import com.red_escuela.backend.dto.ResetPasswordRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.AuthProvider;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private void persistPerson(String username, String rawPassword) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode(rawPassword));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
    }

    @Test
    void loginConCredencialesValidasDevuelve200ConTokens() throws Exception {
        persistPerson("controller1", "correcta123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("controller1", "correcta123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401() throws Exception {
        persistPerson("controller2", "correcta123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("controller2", "mala"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginConBodyInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotaYElTokenViejoDevuelve401() throws Exception {
        persistPerson("controller3", "correcta123");

        String loginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("controller3", "correcta123"))))
                .andReturn().getResponse().getContentAsString();
        AuthResponse original = objectMapper.readValue(loginBody, AuthResponse.class);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(original.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(original.refreshToken())));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(original.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDevuelve204YElTokenDejaDeServir() throws Exception {
        persistPerson("controller4", "correcta123");

        String loginBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("controller4", "correcta123"))))
                .andReturn().getResponse().getContentAsString();
        AuthResponse tokens = objectMapper.readValue(loginBody, AuthResponse.class);

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordDevuelve202SeaCualSeaElEmail() throws Exception {
        persistPerson("controller5", "correcta123");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("controller5@example.com"))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("no-existe@example.com"))))
                .andExpect(status().isAccepted());
    }

    @Test
    void resetPasswordConTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("token-que-no-existe", "nueva123"))))
                .andExpect(status().isUnauthorized());
    }
}

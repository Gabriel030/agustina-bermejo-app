package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.LoginRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthRefreshCookieControllerTest extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private void persistPerson(String username) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode("correcta123"));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
    }

    private MvcResult login(String username) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "correcta123"))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull();
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private MvcResult cookieRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header("User-Agent", "test-browser")
                        .header("X-Requested-With", "fetch")
                        .header("Origin", ALLOWED_ORIGIN))
                .andReturn();
    }

    @Test
    void loginSetsTheRefreshCookieAndKeepsTheTokenOutOfTheBody() throws Exception {
        persistPerson("cookie1");

        MvcResult result = login("cookie1");

        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");
        assertThat(cookieValue(result)).isNotBlank();
    }

    @Test
    void refreshWithCookieRotatesTheCookieAndTheOldOneStopsWorkingAfterTheGraceWindow() throws Exception {
        persistPerson("cookie2");
        String original = cookieValue(login("cookie2"));

        MvcResult refreshed = cookieRefresh(original);

        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookieValue(refreshed)).isNotEqualTo(original);
        assertThat(cookieRefresh(original).getResponse().getStatus()).isEqualTo(200);
        assertThat(cookieRefresh(original).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithCookieButWithoutTheCustomHeaderIsRejectedWith403() throws Exception {
        persistPerson("cookie3");
        String token = cookieValue(login("cookie3"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", token))
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REFRESH_CSRF_REJECTED"));
    }

    @Test
    void refreshWithCookieFromAForeignOriginIsRejectedWith403() throws Exception {
        persistPerson("cookie4");
        String token = cookieValue(login("cookie4"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", token))
                        .header("X-Requested-With", "fetch")
                        .header("Origin", "https://evil.example.org"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectedCsrfAttemptDoesNotRevokeTheSession() throws Exception {
        persistPerson("cookie5");
        String token = cookieValue(login("cookie5"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", token))
                        .header("Origin", "https://evil.example.org"))
                .andExpect(status().isForbidden());

        assertThat(cookieRefresh(token).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refreshWithoutCookieNorBodyReturns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshWithAnUnknownCookieTokenReturns401() throws Exception {
        assertThat(cookieRefresh("not-a-real-token").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void oldClientSendingTheTokenInTheBodyWithoutHeadersStillRefreshesAndReceivesTheCookie() throws Exception {
        persistPerson("cookie6");
        String original = cookieValue(login("cookie6"));

        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + original + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly");
        assertThat(cookieValue(result)).isNotEqualTo(original);
    }

    @Test
    void logoutWithCookieClearsItRevokesTheSessionAndReturns204() throws Exception {
        persistPerson("cookie7");
        String token = cookieValue(login("cookie7"));

        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", token))
                        .header("X-Requested-With", "fetch")
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/api/auth");
        assertThat(cookieRefresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutWithCookieButWithoutTheCustomHeaderIsRejectedAndKeepsTheSession() throws Exception {
        persistPerson("cookie8");
        String token = cookieValue(login("cookie8"));

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isForbidden());

        assertThat(cookieRefresh(token).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoutWithoutAnyTokenStillClearsTheCookieAndReturns204() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void preflightForTheCustomHeaderIsAllowedFromTheConfiguredOrigin() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/auth/refresh")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-requested-with"))
                .andExpect(status().isOk());
    }
}

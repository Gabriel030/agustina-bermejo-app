package com.red_escuela.backend.config;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFailureStatusTest extends AbstractIntegrationTest {
    private static final String PROTECTED_PATH = "/platform/features/ANY/preview";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void requestWithoutTokenIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void requestWithGarbageTokenIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void requestWithExpiredTokenIsRejectedWithUnauthorized() throws Exception {
        String expiredToken = new JwtService("test-secret-not-for-production-use-only-in-tests", -1)
                .generateAccessToken(personWithRole("PLATFORM_ADMIN"));

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void validTokenWithoutTheRequiredRoleIsRejectedWithForbidden() throws Exception {
        String token = jwtService.generateAccessToken(personWithRole("DOCENTE"));

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static Person personWithRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Person person = new Person();
        person.setId(7L);
        person.setRoles(Set.of(role));
        return person;
    }
}

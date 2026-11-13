package com.red_escuela.backend.config;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@org.springframework.transaction.annotation.Transactional
class AuthenticationFailureStatusTest extends AbstractIntegrationTest {
    private static final String PROTECTED_PATH = "/platform/features/ANY/preview";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Long activePersonId() {
        com.red_escuela.backend.shared.domain.Address address = new com.red_escuela.backend.shared.domain.Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername("auth-status-" + System.nanoTime());
        person.setAddress(address);
        person.setStatus(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person.getId();
    }

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
        String expiredToken = new JwtService("test-secret-not-for-production-use-only-in-tests", -1, "agustina-bermejo-app")
                .generateAccessToken(personWithRole(7L, "PLATFORM_ADMIN"));

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void validTokenWithoutTheRequiredRoleIsRejectedWithForbidden() throws Exception {
        String token = jwtService.generateAccessToken(personWithRole(activePersonId(), "DOCENTE"));

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static Person personWithRole(Long id, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Person person = new Person();
        person.setId(id);
        person.setRoles(Set.of(role));
        return person;
    }
}

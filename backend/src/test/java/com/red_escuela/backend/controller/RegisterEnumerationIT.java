package com.red_escuela.backend.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.SharedPropertyOverrides;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SharedPropertyOverrides
class RegisterEnumerationIT extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private Long institutionId() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private void persistPerson(String username, String email) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(email);
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
    }

    private MvcResult register(String username, String email, Long institutionId, String ip) throws Exception {
        RegisterRequest request = new RegisterRequest("Ana", "Perez", email, username, "Password123", institutionId,
                true, true, LocalDate.of(2000, 1, 15), null, null, null, null, true, null);
        return mockMvc.perform(post("/auth/register")
                        .with(servletRequest -> {
                            servletRequest.setRemoteAddr(ip);
                            return servletRequest;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Test
    void existingUsernameConflictDoesNotEchoTheEnteredUsernameOrEmail() throws Exception {
        persistPerson("taken-one", "taken-one@example.com");

        MvcResult result = register("taken-one", "fresh-one@example.com", institutionId(), "10.1.0.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("USERNAME_ALREADY_EXISTS").doesNotContain("taken-one").doesNotContain("fresh-one");
    }

    @Test
    void conflictsForTwoDifferentExistingUsersHaveTheSameBody() throws Exception {
        persistPerson("taken-a", "taken-a@example.com");
        persistPerson("taken-b", "taken-b@example.com");
        Long institutionId = institutionId();

        JsonNode first = objectMapper.readTree(register("taken-a", "x1@example.com", institutionId, "10.1.0.2")
                .getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(register("taken-b", "x2@example.com", institutionId, "10.1.0.3")
                .getResponse().getContentAsString());

        assertThat(first.get("code").asString()).isEqualTo("USERNAME_ALREADY_EXISTS");
        assertThat(first.get("message")).isEqualTo(second.get("message"));
        assertThat(first.get("code")).isEqualTo(second.get("code"));
        assertThat(first.propertyNames()).containsExactlyInAnyOrderElementsOf(second.propertyNames());
    }

    @Test
    void repeatedAttemptsForTheSameUsernameFromTheSameIpAreRateLimited() throws Exception {
        Long institutionId = institutionId();
        register("probe-user", "p1@example.com", institutionId, "10.1.0.4");
        register("PROBE-user", "p2@example.com", institutionId, "10.1.0.4");

        MvcResult third = register(" probe-USER", "p3@example.com", institutionId, "10.1.0.4");

        assertThat(third.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void aThirdPartyExhaustingItsQuotaForAUsernameDoesNotBlockTheOwnerFromAnotherIp() throws Exception {
        Long institutionId = institutionId();
        register("victim-user", "v1@example.com", institutionId, "10.1.0.66");
        register("victim-user", "v2@example.com", institutionId, "10.1.0.66");
        assertThat(register("victim-user", "v3@example.com", institutionId, "10.1.0.66").getResponse().getStatus())
                .isEqualTo(429);

        MvcResult owner = register("victim-user", "owner@example.com", institutionId, "10.1.0.7");

        assertThat(owner.getResponse().getStatus()).isNotEqualTo(429);
    }
}

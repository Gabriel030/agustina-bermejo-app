package com.red_escuela.backend.identity.usecase;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.PasswordResetCodeIssuer;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuditedPasswordActionsIT extends AbstractIntegrationTest {

    private static final String CURRENT_PASSWORD = "Current-Secret-1234";
    private static final String NEW_PASSWORD = "Brand-New-Secret-5678";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordResetCodeIssuer passwordResetCodeIssuer;

    private Person person;
    private String token;

    @BeforeEach
    void prepare() {
        transactions.executeWithoutResult(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person created = new Person();
            String unique = UUID.randomUUID().toString();
            created.setName("Persona");
            created.setLastname(unique);
            created.setDni(unique.substring(0, 12));
            created.setUsername("audited." + unique);
            created.setEmail("audited." + unique + "@example.com");
            created.setPassword(passwordEncoder.encode(CURRENT_PASSWORD));
            created.setAddress(address);
            created.setStatus(StatusType.ACTIVE);
            Role role = roleRepository.findByName("DIRECTOR").orElseThrow();
            created.getRoles().add(role);
            entityManager.persist(created);
            entityManager.flush();
            person = created;
            token = jwtService.generateAccessToken(created);
        });
    }

    @Test
    void changingTheOwnPasswordIsAuditedWithTheActorAndWithoutAnyPasswordOrPersonalData() throws Exception {
        mockMvc.perform(put("/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + CURRENT_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = auditRows("PASSWORD_CHANGED");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("entity_id")).isEqualTo(person.getId());
        assertThat(row.get("actor_person_id")).isEqualTo(person.getId());
        assertThat(row.get("actor_role")).isEqualTo("DIRECTOR");
        assertThat(row.toString()).doesNotContain(CURRENT_PASSWORD, NEW_PASSWORD, person.getUsername(), "@example.com");
    }

    @Test
    void aRejectedPasswordChangeLeavesNoAuditEntry() throws Exception {
        mockMvc.perform(put("/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-password-0000\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(auditRows("PASSWORD_CHANGED")).isEmpty();
    }

    @Test
    void requestingAResetForAnExistingEmailIsAuditedWithoutTheEmailOrTheCode() {
        transactions.executeWithoutResult(status -> passwordResetCodeIssuer.issuePasswordResetCode(person.emailValue()));

        List<Map<String, Object>> rows = auditRows("PASSWORD_RESET_REQUESTED");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("entity_id")).isEqualTo(person.getId());
        assertThat(rows.get(0).get("actor_person_id")).isNull();
        assertThat(rows.get(0).toString()).doesNotContain("@example.com");
    }

    @Test
    void requestingAResetForAnUnknownEmailLeavesNoAuditEntry() {
        Integer before = jdbc.queryForObject("select count(*) from audit_log", Integer.class);

        transactions.executeWithoutResult(status ->
                passwordResetCodeIssuer.issuePasswordResetCode("nobody." + UUID.randomUUID() + "@example.com"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(before);
    }

    private List<Map<String, Object>> auditRows(String action) {
        return jdbc.queryForList("select * from audit_log where action = ? and entity_id = ?", action, person.getId());
    }
}

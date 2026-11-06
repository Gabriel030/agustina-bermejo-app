package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InstitutionAcademicSettingsControllerIT extends AbstractIntegrationTest {

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

    private String directorToken;
    private String teacherToken;

    @BeforeEach
    void createInstitutionAndUsers() {
        transactions.executeWithoutResult(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            jdbc.update("update institution set justification_deadline_business_days = 5, evaluation_config_due_days = 15, announcement_email_enabled = false");
            directorToken = jwtService.generateAccessToken(persistPerson("DIRECTOR"));
            teacherToken = jwtService.generateAccessToken(persistPerson("DOCENTE"));
        });
    }

    @Test
    void getReturnsTheDefaultsOfAFreshInstitution() throws Exception {
        mockMvc.perform(get("/institution/academic-settings").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.justificationDeadlineBusinessDays").value(5))
                .andExpect(jsonPath("$.evaluationConfigDueDays").value(15));
    }

    @Test
    void updatePersistsAndIsReadBack() throws Exception {
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":3,\"evaluationConfigDueDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.justificationDeadlineBusinessDays").value(3));

        assertThat(storedSettings()).isEqualTo("3-30");
        mockMvc.perform(get("/institution/academic-settings").header("Authorization", "Bearer " + teacherToken))
                .andExpect(jsonPath("$.evaluationConfigDueDays").value(30));
    }

    @Test
    void theEmailSwitchIsPersistedReadBackAndKeptWhenOmitted() throws Exception {
        mockMvc.perform(get("/institution/academic-settings").header("Authorization", "Bearer " + teacherToken))
                .andExpect(jsonPath("$.announcementEmailEnabled").value(false));
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":5,\"evaluationConfigDueDays\":15,\"announcementEmailEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcementEmailEnabled").value(true));
        assertThat(storedEmailSwitch()).isTrue();

        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":4,\"evaluationConfigDueDays\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcementEmailEnabled").value(true));
        assertThat(storedEmailSwitch()).isTrue();
    }

    @Test
    void aTeacherCannotTurnTheEmailSwitchOn() throws Exception {
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":5,\"evaluationConfigDueDays\":15,\"announcementEmailEnabled\":true}"))
                .andExpect(status().isForbidden());

        assertThat(storedEmailSwitch()).isFalse();
    }

    @Test
    void updateRejectsOutOfRangeValues() throws Exception {
        rejects("{\"justificationDeadlineBusinessDays\":61,\"evaluationConfigDueDays\":15}");
        rejects("{\"justificationDeadlineBusinessDays\":-1,\"evaluationConfigDueDays\":15}");
        rejects("{\"justificationDeadlineBusinessDays\":5,\"evaluationConfigDueDays\":0}");
        rejects("{\"justificationDeadlineBusinessDays\":5,\"evaluationConfigDueDays\":121}");

        assertThat(storedSettings()).isEqualTo("5-15");
    }

    @Test
    void updateAcceptsTheBoundaryValues() throws Exception {
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":0,\"evaluationConfigDueDays\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":60,\"evaluationConfigDueDays\":120}"))
                .andExpect(status().isOk());

        assertThat(storedSettings()).isEqualTo("60-120");
    }

    @Test
    void aTeacherCannotUpdateTheSettings() throws Exception {
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificationDeadlineBusinessDays\":3,\"evaluationConfigDueDays\":30}"))
                .andExpect(status().isForbidden());

        assertThat(storedSettings()).isEqualTo("5-15");
    }

    private void rejects(String body) throws Exception {
        mockMvc.perform(put("/institution/academic-settings").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private boolean storedEmailSwitch() {
        return Boolean.TRUE.equals(jdbc.queryForObject("select announcement_email_enabled from institution", Boolean.class));
    }

    private String storedSettings() {
        return jdbc.queryForObject(
                "select justification_deadline_business_days || '-' || evaluation_config_due_days from institution",
                String.class);
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("settings." + suffix);
        person.setEmail("settings." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}

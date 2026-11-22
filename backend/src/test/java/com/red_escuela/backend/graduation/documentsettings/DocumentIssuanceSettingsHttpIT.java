package com.red_escuela.backend.graduation.documentsettings;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentIssuanceSettingsHttpIT extends AbstractIntegrationTest {

    private static final String URL = "/certificate-settings/documents";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private DocumentIssuanceSettings documentIssuanceSettings;

    private String director;
    private String administrative;
    private String student;

    @BeforeEach
    void createUsers() {
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
        student = tokenFor("ALUMNO", 3);
    }

    private String tokenFor(String roleName, int index) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Config");
        person.setLastname(roleName);
        person.setUsername("docsettings." + roleName.toLowerCase());
        person.setDni(String.valueOf(93_000_000 + index));
        person.setEmail("docsettings." + roleName.toLowerCase() + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private ResultActions read(String token) throws Exception {
        return mockMvc.perform(get(URL).header("Authorization", "Bearer " + token));
    }

    private ResultActions write(String token, String body) throws Exception {
        return mockMvc.perform(put(URL).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(int advance, int attendance, int approved, int limit, long version) {
        return "{\"examRegistrationAdvanceDays\":" + advance + ",\"examAttendanceValidityDays\":" + attendance
                + ",\"approvedSubjectsValidityDays\":" + approved + ",\"dailyIssuanceLimitPerType\":" + limit + ",\"version\":" + version + "}";
    }

    @Test
    void seededDefaultsAreReturned() throws Exception {
        read(director).andExpect(status().isOk())
                .andExpect(jsonPath("$.examRegistrationAdvanceDays").value(15))
                .andExpect(jsonPath("$.examAttendanceValidityDays").value(30))
                .andExpect(jsonPath("$.approvedSubjectsValidityDays").value(30))
                .andExpect(jsonPath("$.dailyIssuanceLimitPerType").value(1))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void directorUpdatesSettingsAndPortReadsThem() throws Exception {
        write(director, body(20, 45, 60, 3, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.examRegistrationAdvanceDays").value(20))
                .andExpect(jsonPath("$.dailyIssuanceLimitPerType").value(3))
                .andExpect(jsonPath("$.version").value(1));

        read(administrative).andExpect(status().isOk()).andExpect(jsonPath("$.examAttendanceValidityDays").value(45));
        DocumentIssuanceSettings.DocumentIssuanceValues values = documentIssuanceSettings.current();
        assertThat(values.examRegistrationAdvanceDays()).isEqualTo(20);
        assertThat(values.examAttendanceValidityDays()).isEqualTo(45);
        assertThat(values.approvedSubjectsValidityDays()).isEqualTo(60);
        assertThat(values.dailyIssuanceLimitPerType()).isEqualTo(3);
    }

    @Test
    void administrativeCanReadButNotWrite() throws Exception {
        read(administrative).andExpect(status().isOk());
        write(administrative, body(20, 45, 60, 3, 0)).andExpect(status().isForbidden());
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        read(student).andExpect(status().isForbidden());
        write(student, body(20, 45, 60, 3, 0)).andExpect(status().isForbidden());
    }

    @Test
    void staleVersionReturnsConflict() throws Exception {
        write(director, body(20, 45, 60, 3, 0)).andExpect(status().isOk());
        write(director, body(10, 10, 10, 2, 0)).andExpect(status().isConflict());
        read(director).andExpect(jsonPath("$.examRegistrationAdvanceDays").value(20));
    }

    @Test
    void outOfRangeOrMissingValuesAreRejected() throws Exception {
        write(director, body(-1, 30, 30, 1, 0)).andExpect(status().isBadRequest());
        write(director, body(366, 30, 30, 1, 0)).andExpect(status().isBadRequest());
        write(director, body(15, 0, 30, 1, 0)).andExpect(status().isBadRequest());
        write(director, body(15, 30, 366, 1, 0)).andExpect(status().isBadRequest());
        write(director, body(15, 30, 30, 0, 0)).andExpect(status().isBadRequest());
        write(director, body(15, 30, 30, 51, 0)).andExpect(status().isBadRequest());
        write(director, "{\"examRegistrationAdvanceDays\":15}").andExpect(status().isBadRequest());
        write(director, body(0, 1, 1, 1, 0)).andExpect(status().isOk());
    }

    @Test
    void updateRecordsAuditWithBeforeAndAfter() throws Exception {
        write(director, body(20, 45, 60, 3, 0)).andExpect(status().isOk());

        List<AuditLogEntry> entries = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.DOCUMENT_SETTINGS_CHANGED).toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getBeforeValue()).contains("advance=15", "limit=1");
        assertThat(entries.get(0).getAfterValue()).contains("advance=20", "limit=3");
    }
}

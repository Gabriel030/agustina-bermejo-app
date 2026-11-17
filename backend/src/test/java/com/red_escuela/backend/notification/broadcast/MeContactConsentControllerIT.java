package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeContactConsentControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private static String body(String channel, String category, boolean granted) {
        return "{\"channel\":\"" + channel + "\",\"category\":\"" + category + "\",\"granted\":" + granted + "}";
    }

    @Test
    void listsTheDefaultMatrix() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "me-consent-matrix");

        mockMvc.perform(get("/me/contact-consents").header("Authorization", bearer(person)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL' && @.category=='INSTITUTIONAL')].granted").value(true))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL' && @.category=='PROMOTIONAL')].granted").value(false))
                .andExpect(jsonPath("$[?(@.channel=='WHATSAPP' && @.category=='INSTITUTIONAL')].granted").value(true))
                .andExpect(jsonPath("$[?(@.channel=='WHATSAPP' && @.category=='PROMOTIONAL')].granted").value(false));
    }

    @Test
    void updatesOnlyOwnConsent() throws Exception {
        Person owner = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "me-consent-owner");
        Person other = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "me-consent-other");

        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body("WHATSAPP", "PROMOTIONAL", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.category").value("PROMOTIONAL"))
                .andExpect(jsonPath("$.granted").value(true))
                .andExpect(jsonPath("$.recordedAt").isNotEmpty());

        assertThat(jdbc.queryForObject("select count(*) from contact_consent where person_id = ? and status = 'GRANTED'",
                Integer.class, owner.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from contact_consent where person_id = ?", Integer.class, other.getId())).isZero();
        mockMvc.perform(get("/me/contact-consents").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$[?(@.channel=='WHATSAPP' && @.category=='PROMOTIONAL')].granted").value(false));
    }

    @Test
    void recordsAuditEntryOnChange() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "me-consent-audit");

        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", "PROMOTIONAL", true))).andExpect(status().isOk());
        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                .contentType(MediaType.APPLICATION_JSON).content(body("EMAIL", "PROMOTIONAL", false))).andExpect(status().isOk());

        assertThat(jdbc.queryForList("select action from audit_log where entity_id = ? and action like 'CONTACT_CONSENT%' order by id",
                String.class, person.getId())).containsExactly("CONTACT_CONSENT_GRANTED", "CONTACT_CONSENT_REVOKED");
        assertThat(jdbc.queryForObject("select count(*) from contact_consent where person_id = ?", Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void rejectsStorageChannelsAndMissingFields() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "me-consent-invalid");

        for (String invalid : new String[]{body("WHATSAPP_API", "PROMOTIONAL", true), body("EMAIL", "BROADCAST", true),
                "{\"channel\":\"EMAIL\",\"category\":\"PROMOTIONAL\"}"}) {
            mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
    }
}

package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountCreationConsentIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private JwtService jwtService;

    private Long institutionId() {
        Institution institution = new Institution();
        institution.setName("Instituto de consentimiento");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private String registration(String suffix, Long institutionId, String consentsJson) {
        return registration(suffix, institutionId, consentsJson, "true");
    }

    private String registration(String suffix, Long institutionId, String consentsJson, String acceptedNotices) {
        return "{\"nombre\":\"Sofia\",\"apellido\":\"Ramirez\",\"email\":\"consent." + suffix + "@example.com\","
                + "\"username\":\"consent." + suffix + "\",\"password\":\"Test1234\",\"institutionId\":" + institutionId
                + ",\"acceptedTerms\":true,\"acceptedPrivacy\":true,\"birthDate\":\"2000-01-15\""
                + (acceptedNotices == null ? "" : ",\"acceptedInstitutionalNotices\":" + acceptedNotices)
                + (consentsJson == null ? "" : ",\"contactConsents\":" + consentsJson) + "}";
    }

    private int consentRows(String suffix) {
        return jdbc.queryForObject("select count(*) from contact_consent c join person p on p.id = c.person_id where p.username = ?",
                Integer.class, "consent." + suffix);
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    @Test
    void accountCreationConsentIsRecordedWithSource() throws Exception {
        Long institutionId = institutionId();

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("granted", institutionId, null)))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForList("select c.channel, c.category, c.status, c.source from contact_consent c join person p on p.id = c.person_id where p.username = ?",
                "consent.granted")).hasSize(1).allSatisfy(row -> {
            assertThat(row.get("channel")).isEqualTo("EMAIL");
            assertThat(row.get("category")).isEqualTo("INSTITUTIONAL");
            assertThat(row.get("status")).isEqualTo("GRANTED");
            assertThat(row.get("source")).isEqualTo("ACCOUNT_CREATION");
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_log a join person p on p.id = a.entity_id where a.action = 'CONTACT_CONSENT_GRANTED' and a.reason like '%ACCOUNT_CREATION%' and p.username = ?",
                Integer.class, "consent.granted")).isEqualTo(1);
    }

    @Test
    void mandatoryCategoriesAreRejected() throws Exception {
        Long institutionId = institutionId();

        for (String category : new String[] {"BILLING", "SYSTEM_NOTICE", "COLLECTION"}) {
            mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registration("mandatory" + category, institutionId, "[{\"category\":\"" + category + "\",\"granted\":true}]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONSENT_CATEGORY_NOT_OPTIONAL"));
        }

        for (String category : new String[] {"BILLING", "SYSTEM_NOTICE", "COLLECTION"}) {
            assertThat(consentRows("mandatory" + category)).isZero();
        }
    }

    @Test
    void promotionalRejectedWhenDisabled() throws Exception {
        Long institutionId = institutionId();

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("promo", institutionId, "[{\"category\":\"PROMOTIONAL\",\"granted\":true}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTIONAL_CONSENT_DISABLED"));

        assertThat(consentRows("promo")).isZero();
    }

    @Test
    void skippingLeavesNoRecord() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "skipped-consent");

        assertThat(jdbc.queryForObject("select count(*) from contact_consent where person_id = ?", Integer.class, person.getId())).isZero();
        mockMvc.perform(get("/me/contact-consents").header("Authorization", bearer(person)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.channel=='EMAIL' && @.category=='INSTITUTIONAL')].hasRecord").value(false));
    }

    @Test
    void registrationRecordsOnlyInstitutionalNotices() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("only-institutional", institutionId(), null)))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForList("select c.category from contact_consent c join person p on p.id = c.person_id where p.username = ?",
                "consent.only-institutional")).extracting(row -> row.get("category")).containsExactly("INSTITUTIONAL");
    }

    @Test
    void institutionalNoticesAcceptanceIsRequired() throws Exception {
        Long institutionId = institutionId();

        for (String accepted : new String[] {null, "false"}) {
            String suffix = "required" + accepted;
            mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registration(suffix, institutionId, null, accepted)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));
            assertThat(consentRows(suffix)).isZero();
        }
    }

    @Test
    void firstLoginConsentUsesAccountCreationSourceOnlyOnce() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "first-login-consent");
        String body = "{\"channel\":\"EMAIL\",\"category\":\"INSTITUTIONAL\",\"granted\":true,\"source\":\"ACCOUNT_CREATION\"}";

        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasRecord").value(true));
        assertThat(jdbc.queryForObject("select source from contact_consent where person_id = ? and channel = 'EMAIL'",
                String.class, person.getId())).isEqualTo("ACCOUNT_CREATION");

        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTACT_CONSENT_ALREADY_RECORDED"));
    }

    @Test
    void foreignSourcesAndDisabledPromotionalAreRejectedOnUpdate() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "first-login-rejects");

        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"category\":\"INSTITUTIONAL\",\"granted\":true,\"source\":\"STAFF_ON_BEHALF\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/me/contact-consents").header("Authorization", bearer(person))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"category\":\"PROMOTIONAL\",\"granted\":true,\"source\":\"ACCOUNT_CREATION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTIONAL_CONSENT_DISABLED"));
        assertThat(jdbc.queryForObject("select count(*) from contact_consent where person_id = ?", Integer.class, person.getId())).isZero();
    }
}

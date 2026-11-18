package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountCreationConsentRollbackIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failingConsentRecordingLeavesNoPersonNorConsentNorNewsletter() throws Exception {
        Long institutionId = new TransactionTemplate(transactionManager).execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de rollback");
            entityManager.persist(institution);
            entityManager.flush();
            return institution.getId();
        });
        String body = "{\"nombre\":\"Sofia\",\"apellido\":\"Ramirez\",\"email\":\"rollback.consent@example.com\","
                + "\"username\":\"rollback.consent\",\"password\":\"Test1234\",\"institutionId\":" + institutionId
                + ",\"acceptedTerms\":true,\"acceptedPrivacy\":true,\"birthDate\":\"2000-01-15\","
                + "\"acceptedNewsletter\":true,\"acceptedInstitutionalNotices\":true,"
                + "\"contactConsents\":[{\"category\":\"PROMOTIONAL\",\"granted\":false},{\"category\":\"PROMOTIONAL\",\"granted\":false}]}";

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_CATEGORY_REPEATED"));

        assertThat(jdbc.queryForObject("select count(*) from person where username = 'rollback.consent'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from contact_consent", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from newsletter_preference", Integer.class)).isZero();
    }
}

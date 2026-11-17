package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenIssuer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicUnsubscribeControllerIT extends AbstractIntegrationTest {

    private static final AtomicInteger CLIENTS = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UnsubscribeTokenIssuer issuer;

    @Autowired
    private ContactConsentLedger ledger;

    private static RequestPostProcessor freshClient() {
        String address = "198.51.100." + CLIENTS.incrementAndGet();
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private int consentRows(Long personId) {
        return jdbc.queryForObject("select count(*) from contact_consent where person_id = ?", Integer.class, personId);
    }

    @Test
    void getDoesNotChangeState() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "unsub-get");
        String token = issuer.issueToken(person.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);

        mockMvc.perform(get("/public/unsubscribe/" + token).with(freshClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.category").value("PROMOTIONAL"))
                .andExpect(jsonPath("$.length()").value(2));

        assertThat(consentRows(person.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and action like 'CONTACT_CONSENT%'",
                Integer.class, person.getId())).isZero();
    }

    @Test
    void postRevokesAndIsIdempotent() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "unsub-post");
        ledger.recordConsent(person.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, true,
                com.red_escuela.backend.notification.broadcast.domain.ConsentSource.PROFILE, null);
        String token = issuer.issueToken(person.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);

        mockMvc.perform(post("/public/unsubscribe/" + token).with(freshClient())).andExpect(status().isOk());
        mockMvc.perform(post("/public/unsubscribe/" + token).with(freshClient())).andExpect(status().isOk());

        assertThat(ledger.isAllowed(person.getId(), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL).allowed()).isFalse();
        assertThat(jdbc.queryForObject("select source from contact_consent where person_id = ?", String.class, person.getId()))
                .isEqualTo("EMAIL_LINK");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and action = 'CONTACT_CONSENT_REVOKED'",
                Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void whatsappTokenRevokesBothStorageChannels() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "unsub-wa");
        String token = issuer.issueToken(person.getId(), ConsentChannel.WHATSAPP, BroadcastCategory.INSTITUTIONAL);

        mockMvc.perform(post("/public/unsubscribe/" + token).with(freshClient())).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from contact_consent where person_id = ? and status = 'REVOKED'",
                Integer.class, person.getId())).isEqualTo(2);
    }

    @Test
    void invalidTokenReturns404WithoutDetails() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "unsub-invalid");
        String token = issuer.issueToken(person.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/public/unsubscribe/" + tampered), post("/public/unsubscribe/" + tampered), get("/public/unsubscribe/garbage")}) {
            mockMvc.perform(request.with(freshClient()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVALID_UNSUBSCRIBE_TOKEN"))
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unsub-invalid"))));
        }
        assertThat(consentRows(person.getId())).isZero();
    }

    @Test
    void worksWithoutAuthentication() throws Exception {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "unsub-anon");
        String token = issuer.issueToken(person.getId(), ConsentChannel.EMAIL, BroadcastCategory.INSTITUTIONAL);

        mockMvc.perform(get("/public/unsubscribe/" + token).with(freshClient())).andExpect(status().isOk());
        mockMvc.perform(post("/public/unsubscribe/" + token).with(freshClient())).andExpect(status().isOk());
    }
}

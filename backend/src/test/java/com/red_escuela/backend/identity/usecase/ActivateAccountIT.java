package com.red_escuela.backend.identity.usecase;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.ActivationTokenIssuer;
import com.red_escuela.backend.identity.api.IssuedActivationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.PersonAlreadyActivatedException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ActivateAccountIT extends AbstractIntegrationTest {

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
    private RoleRepository roleRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ActivationTokenIssuer activationTokenIssuer;

    private Person person;

    @BeforeEach
    void prepareImportedPerson() {
        person = createPerson(null);
    }

    private Person createPerson(String encodedPassword) {
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person created = new Person();
            String unique = UUID.randomUUID().toString();
            created.setName("Importada");
            created.setLastname(unique);
            created.setDni(unique.substring(0, 12));
            created.setUsername("activation." + unique);
            created.setEmail("activation." + unique + "@example.com");
            created.setPassword(encodedPassword);
            created.setGrantSystemAccess(false);
            created.setEmailVerified(false);
            created.setAddress(address);
            created.setStatus(StatusType.ACTIVE);
            Role role = roleRepository.findByName("ALUMNO").orElseThrow();
            created.getRoles().add(role);
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
    }

    private ResultActions activate(String token, String password) throws Exception {
        return mockMvc.perform(post("/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private void expireToken(String token) {
        jdbc.update("update activation_token set expires_at = now() - interval '3 days' where token_hash = ?",
                TokenHashUtil.sha256Hex(token));
    }

    @Test
    void redeemingAValidTokenSetsPasswordVerifiesEmailAndGrantsSystemAccess() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());

        Person reloaded = personRepository.findById(person.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPassword())).isTrue();
        assertThat(reloaded.getEmailVerified()).isTrue();
        assertThat(reloaded.getGrantSystemAccess()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ? and used_at is not null",
                Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        List<String> stored = jdbc.queryForList("select token_hash from activation_token where person_id = ?",
                String.class, person.getId());

        assertThat(stored).containsExactly(TokenHashUtil.sha256Hex(issued.token()));
        assertThat(stored.get(0)).isNotEqualTo(issued.token());
        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void anExpiredTokenIsRejectedWithExpiredCodeAndDoesNotActivateThePerson() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        expireToken(issued.token());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_EXPIRED"));

        Person reloaded = personRepository.findById(person.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isNull();
        assertThat(reloaded.getGrantSystemAccess()).isFalse();
    }

    @Test
    void aUsedTokenCannotBeRedeemedTwice() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());

        activate(issued.token(), "Another-Secret-9999").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));

        Person reloaded = personRepository.findById(person.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPassword())).isTrue();
    }

    @Test
    void reissuingInvalidatesThePreviousToken() throws Exception {
        IssuedActivationToken first = activationTokenIssuer.issueActivationToken(person.getId());
        IssuedActivationToken second = activationTokenIssuer.issueActivationToken(person.getId());

        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ?",
                Integer.class, person.getId())).isEqualTo(1);
        activate(first.token(), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));
        activate(second.token(), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void unknownAndUsedTokensAnswerTheSameBody() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());

        String usedBody = activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.retryable").value(false))
                .andReturn().getResponse().getContentAsString();
        String unknownBody = activate("unknown-token-that-never-existed", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownBody.replaceAll("\"requestId\":\"[^\"]*\"", "")).isEqualTo(usedBody.replaceAll("\"requestId\":\"[^\"]*\"", ""));
    }

    @Test
    void aPasswordBreakingThePolicyIsRejectedAndKeepsTheTokenRedeemable() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        activate(issued.token(), "short").andExpect(status().isBadRequest());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void issuingForAPersonWhoAlreadyHasAPasswordIsRejected() {
        Person active = createPerson(passwordEncoder.encode(NEW_PASSWORD));

        assertThatThrownBy(() -> activationTokenIssuer.issueActivationToken(active.getId()))
                .isInstanceOf(PersonAlreadyActivatedException.class);
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ?",
                Integer.class, active.getId())).isZero();
    }

    @Test
    void aTokenIssuedBeforeThePersonSetAPasswordElsewhereIsRejected() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        transactions.executeWithoutResult(status -> {
            Person managed = personRepository.findById(person.getId()).orElseThrow();
            managed.setPassword(passwordEncoder.encode("Other-Secret-1234"));
            personRepository.save(managed);
        });

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));
    }
}

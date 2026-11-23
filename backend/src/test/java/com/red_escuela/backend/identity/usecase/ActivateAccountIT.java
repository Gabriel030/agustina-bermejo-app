package com.red_escuela.backend.identity.usecase;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.ActivationTokenIssuer;
import com.red_escuela.backend.identity.api.IssuedActivationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.PersonNotEligibleForActivationException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired
    private ActivationTokenIssuer activationTokenIssuer;

    private Person person;

    @BeforeEach
    void prepareImportedPerson() {
        person = createPerson(null);
    }

    private Person createPerson(String encodedPassword) {
        return createPerson(encodedPassword, StatusType.ACTIVE, "ALUMNO");
    }

    private Person createPerson(String encodedPassword, StatusType statusType, String roleName) {
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
            created.setStatus(statusType);
            Role role = roleRepository.findByName(roleName).orElseThrow();
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

    @Test
    void anEmailChangeStartedByAThirdPartyBlocksTheRedemptionWithTheInvalidBody() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        transactions.executeWithoutResult(status -> {
            EmailVerificationToken pending = new EmailVerificationToken();
            pending.setPerson(entityManager.getReference(Person.class, person.getId()));
            pending.setTokenHash(UUID.randomUUID().toString());
            pending.setNewEmail("attacker." + UUID.randomUUID() + "@example.com");
            pending.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
            emailVerificationTokenRepository.save(pending);
        });

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));

        assertThat(personRepository.findById(person.getId()).orElseThrow().getPassword()).isNull();
    }

    @Test
    void changingTheEmailAfterIssuingInvalidatesTheRedemption() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        jdbc.update("update person set email = ? where id = ?", "changed." + UUID.randomUUID() + "@example.com", person.getId());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));

        Person reloaded = personRepository.findById(person.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isNull();
        assertThat(reloaded.getEmailVerified()).isFalse();
    }

    @Test
    void issuingIsRejectedForInactiveDeletedStaffAndMailLessPeople() {
        Person inactive = createPerson(null, StatusType.INACTIVE, "ALUMNO");
        Person deleted = createPerson(null, StatusType.DELETED, "ALUMNO");
        Person staff = createPerson(null, StatusType.ACTIVE, "DOCENTE");
        Person withoutMail = createPerson(null);
        jdbc.update("update person set email = null where id = ?", withoutMail.getId());

        for (Person ineligible : List.of(inactive, deleted, staff, withoutMail)) {
            assertThatThrownBy(() -> activationTokenIssuer.issueActivationToken(ineligible.getId()))
                    .isInstanceOf(PersonNotEligibleForActivationException.class);
            assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ?",
                    Integer.class, ineligible.getId())).isZero();
        }
    }

    @Test
    void aPersonDeactivatedAfterIssuingCannotRedeem() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        jdbc.update("update person set status = 'INACTIVE' where id = ?", person.getId());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));
    }

    @Test
    void redeemingRevokesPreviousSessionsAndBumpsTheTokenVersion() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        Integer before = personRepository.findTokenVersionById(person.getId());
        String sessionHash = UUID.randomUUID().toString();
        transactions.executeWithoutResult(status -> {
            RefreshToken session = new RefreshToken();
            session.setPerson(entityManager.getReference(Person.class, person.getId()));
            session.setTokenHash(sessionHash);
            session.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
            session.setCreatedAt(java.time.LocalDateTime.now());
            entityManager.persist(session);
        });

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(personRepository.findTokenVersionById(person.getId())).isGreaterThan(before);
        assertThat(jdbc.queryForObject("select revoked from refresh_token where token_hash = ?", Boolean.class, sessionHash)).isTrue();
    }

    @Test
    void theEmailComparisonIgnoresCase() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        jdbc.update("update person set email = upper(email) where id = ?", person.getId());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void changingTheEmailAndRestoringTheOriginalKeepsTheRedemptionWorking() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        String original = jdbc.queryForObject("select email from person where id = ?", String.class, person.getId());
        jdbc.update("update person set email = ? where id = ?", "temp." + UUID.randomUUID() + "@example.com", person.getId());
        activate(issued.token(), NEW_PASSWORD).andExpect(status().isBadRequest());
        jdbc.update("update person set email = ? where id = ?", original, person.getId());

        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    private ResultActions tokenStatus(String token) throws Exception {
        String body = token == null ? "{}" : "{\"token\":\"" + token + "\"}";
        return mockMvc.perform(post("/auth/activation-token/status").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr("10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
                    return request;
                }));
    }

    private void ensureInstitution() {
        if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
            jdbc.update("insert into institution (name) values ('Instituto de prueba')");
        }
        jdbc.update("update institution set email = 'contacto@instituto.test', phone_number = '011-5555-0000' where id = (select min(id) from institution)");
        jdbc.update("insert into student (institution_id, person_id, onboarding_status) select min(id), ?, 'CONFIRMADO' from institution",
                person.getId());
    }

    @Test
    void tokenStatusOfAPersonWhoIsNotAStudentHasNoInstitution() throws Exception {
        ensureInstitution();
        jdbc.update("delete from student where person_id = ?", person.getId());
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.institution").doesNotExist());
    }

    @Test
    void validTokenOfAPersonBlockedByAThirdPartyEmailChangeIsInvalidWithoutData() throws Exception {
        ensureInstitution();
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        transactions.executeWithoutResult(status -> {
            EmailVerificationToken pending = new EmailVerificationToken();
            pending.setPerson(entityManager.getReference(Person.class, person.getId()));
            pending.setTokenHash(UUID.randomUUID().toString());
            pending.setNewEmail("attacker." + UUID.randomUUID() + "@example.com");
            pending.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
            emailVerificationTokenRepository.save(pending);
        });

        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.institution").doesNotExist());
    }

    @Test
    void validTokenStatusExposesUsernameAndInstitutionContact() throws Exception {
        ensureInstitution();
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        tokenStatus(issued.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.username").value(person.getUsername()))
                .andExpect(jsonPath("$.institution.name").isNotEmpty())
                .andExpect(jsonPath("$.institution.email").value("contacto@instituto.test"))
                .andExpect(jsonPath("$.institution.phoneNumber").value("011-5555-0000"));
        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("VALID"));
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ? and used_at is null",
                Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void validTokenStatusFallsBackToTheEmailWhenThePersonHasNoUsername() throws Exception {
        ensureInstitution();
        jdbc.update("update person set username = null where id = ?", person.getId());
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.username").value(person.getEmail().value()));
    }

    @Test
    void expiredTokenStatusKeepsTheInstitutionAndHidesTheUsername() throws Exception {
        ensureInstitution();
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        expireToken(issued.token());

        tokenStatus(issued.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.institution.email").value("contacto@instituto.test"));
    }

    @Test
    void unknownUsedBlankAndMissingTokensAnswerTheSameInvalidBody() throws Exception {
        ensureInstitution();
        IssuedActivationToken used = activationTokenIssuer.issueActivationToken(person.getId());
        activate(used.token(), NEW_PASSWORD).andExpect(status().isNoContent());
        Person otherPerson = createPerson(null);
        IssuedActivationToken forActivePerson = activationTokenIssuer.issueActivationToken(otherPerson.getId());
        jdbc.update("update person set password = 'x' where id = ?", otherPerson.getId());

        for (String token : new String[]{"does-not-exist", used.token(), forActivePerson.token(), "", "   ", null}) {
            tokenStatus(token).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INVALID"))
                    .andExpect(jsonPath("$.username").doesNotExist())
                    .andExpect(jsonPath("$.institution").doesNotExist());
        }
    }

    @Test
    void inactivePersonAndExpiredTokenOfAnActivePersonAreInvalid() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        expireToken(issued.token());
        jdbc.update("update person set status = 'INACTIVE' where id = ?", person.getId());

        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.institution").doesNotExist());
    }

    @Test
    void emailChangeAfterIssuingMakesTheStatusInvalid() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());
        jdbc.update("update person set email = ? where id = ?", "changed." + UUID.randomUUID() + "@example.com", person.getId());

        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("INVALID"));
    }

    @Test
    void activationTokenStatusIsRateLimitedPerIp() throws Exception {
        String ip = "10.8.8." + (int) (Math.random() * 250);
        int limited = 0;
        for (int attempt = 0; attempt < 260 && limited == 0; attempt++) {
            int httpStatus = mockMvc.perform(post("/auth/activation-token/status").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"x\"}").with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })).andReturn().getResponse().getStatus();
            if (httpStatus == 429) {
                limited++;
            }
        }
        assertThat(limited).isEqualTo(1);
    }

    @Test
    void activatingStillAnswers204ForAValidTokenAndKeepsItsErrorCodes() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        tokenStatus(issued.token()).andExpect(jsonPath("$.status").value("VALID"));
        activate(issued.token(), NEW_PASSWORD).andExpect(status().isNoContent());
        activate("does-not-exist", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_TOKEN_INVALID"));
    }

    @Test
    void concurrentIssuingLeavesASingleLiveToken() throws Exception {
        List<IssuedActivationToken> issued = runConcurrently(
                () -> activationTokenIssuer.issueActivationToken(person.getId()),
                () -> activationTokenIssuer.issueActivationToken(person.getId()));

        assertThat(issued).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from activation_token where person_id = ?",
                Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void concurrentRedemptionsOfTheSameTokenHaveExactlyOneWinner() throws Exception {
        IssuedActivationToken issued = activationTokenIssuer.issueActivationToken(person.getId());

        List<Integer> statuses = runConcurrently(
                () -> activate(issued.token(), NEW_PASSWORD).andReturn().getResponse().getStatus(),
                () -> activate(issued.token(), NEW_PASSWORD).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(204, 400);
    }

    private <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = List.of(
                    executor.submit(() -> {
                        start.await();
                        return first.call();
                    }),
                    executor.submit(() -> {
                        start.await();
                        return second.call();
                    }));
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}

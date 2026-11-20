package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.infrastructure.AccountAccessStateCache;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetFailureConcurrencyIT extends AbstractIntegrationTest {

    private static final String IP = "10.1.1.1";

    @Autowired
    private VerifyResetCodeUseCase verifyResetCodeUseCase;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private SessionRevoker sessionRevoker;
    @Autowired
    private AccountAccessStateCache accountAccessStateCache;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Person persistPerson(String username) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            person.setUsername(username);
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            return personRepository.save(person);
        });
    }

    private void persistCode(Person person) {
        transactionTemplate.executeWithoutResult(status -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setPerson(personRepository.getReferenceById(person.getId()));
            token.setTokenHash(TokenHashUtil.sha256Hex("123456"));
            token.setExpiresAt(ApplicationTime.now().plusMinutes(15));
            token.setCreatedAt(ApplicationTime.now());
            passwordResetTokenRepository.save(token);
        });
    }

    @Test
    void simultaneousWrongCodesAreAllCountedWithoutLostUpdates() throws Exception {
        Person person = persistPerson("reset-race-" + System.nanoTime());
        persistCode(person);
        int contenders = 5;
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            calls.add(() -> {
                try {
                    verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), "000000", IP));
                    return false;
                } catch (InvalidPasswordResetTokenException expected) {
                    return true;
                }
            });
        }

        List<Outcome<Boolean>> outcomes = ConcurrentRace.run(calls);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        PasswordResetToken token = passwordResetTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(person.getId())).findFirst().orElseThrow();
        assertThat(token.getAttempts()).isEqualTo(contenders);
        assertThat(passwordResetTokenRepository.countBlockingFailures(person.getId(), TokenHashUtil.sha256Hex(IP), contenders,
                ApplicationTime.now().minusDays(1))).isEqualTo(1);
    }

    @Test
    void cacheIsEvictedAgainAfterCommitSoAConcurrentlyCachedStaleStateDoesNotSurvive() {
        Person person = persistPerson("evict-commit-" + System.nanoTime());
        accountAccessStateCache.evict(person.getId());

        transactionTemplate.executeWithoutResult(status -> {
            Person managed = personRepository.findById(person.getId()).orElseThrow();
            sessionRevoker.revokeAllSessions(managed);
            CompletableFuture.runAsync(() -> accountAccessStateCache.find(person.getId())).join();
        });

        assertThat(accountAccessStateCache.find(person.getId())).get().extracting("tokenVersion").isEqualTo(1);
    }

    @Test
    void concurrentFullPersonSavesNeverOverwriteTheAtomicallyIncrementedTokenVersion() throws Exception {
        Person person = persistPerson("tv-race-" + System.nanoTime());
        int revokers = 4;
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < revokers; i++) {
            calls.add(() -> {
                transactionTemplate.executeWithoutResult(status ->
                        sessionRevoker.revokeAllSessions(personRepository.findById(person.getId()).orElseThrow()));
                return true;
            });
            calls.add(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    Person stale = personRepository.findById(person.getId()).orElseThrow();
                    stale.setLastname("Apellido");
                    personRepository.save(stale);
                });
                return true;
            });
        }

        List<Outcome<Boolean>> outcomes = ConcurrentRace.run(calls);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(personRepository.findTokenVersionById(person.getId())).isEqualTo(revokers);
    }
}

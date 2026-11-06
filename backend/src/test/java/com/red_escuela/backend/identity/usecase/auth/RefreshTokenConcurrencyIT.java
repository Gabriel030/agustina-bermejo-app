package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenConcurrencyIT extends AbstractIntegrationTest {

    private static final ClientContext CLIENT = ClientContext.of("browser-a", "10.0.0.1");

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;
    @Autowired
    private AuthTokenIssuer authTokenIssuer;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Person persistPerson(String username) {
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(addressRepository.save(new Address()));
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private long activeSessions(Person person) {
        return refreshTokenRepository.findByPersonAndRevokedFalseAndExpiresAtAfter(
                person, ApplicationTime.now().minusDays(1)).size();
    }

    @Test
    void twoSimultaneousRefreshesOfSameTokenBothSucceedWithoutRevokingSessions() throws Exception {
        Person person = persistPerson("refresh-race-" + System.nanoTime());
        String refreshToken = authTokenIssuer.issueTokens(person).refreshToken();
        Callable<AuthTokens> refresh = () -> refreshTokenUseCase.refresh(refreshToken, CLIENT);

        List<Outcome<AuthTokens>> outcomes = ConcurrentRace.run(List.of(refresh, refresh));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes.get(0).value().refreshToken()).isNotEqualTo(outcomes.get(1).value().refreshToken());
        assertThat(activeSessions(person)).isEqualTo(2);
    }

    @Test
    void threeSimultaneousRefreshesOfSameTokenRevokeAllSessions() throws Exception {
        Person person = persistPerson("refresh-race3-" + System.nanoTime());
        String refreshToken = authTokenIssuer.issueTokens(person).refreshToken();
        Callable<AuthTokens> refresh = () -> refreshTokenUseCase.refresh(refreshToken, CLIENT);

        List<Outcome<AuthTokens>> outcomes = ConcurrentRace.run(List.of(refresh, refresh, refresh));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(2);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .singleElement()
                .satisfies(loser -> assertThat(loser.failure()).isInstanceOf(InvalidRefreshTokenException.class));
        assertThat(activeSessions(person)).isZero();
    }
}

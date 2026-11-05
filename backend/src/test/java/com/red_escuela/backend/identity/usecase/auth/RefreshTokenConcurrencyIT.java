package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
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

    @Test
    void twoSimultaneousRefreshesWithTheSameTokenLeaveExactlyOneNewSession() throws Exception {
        Person person = persistPerson("refresh-race-" + System.nanoTime());
        String refreshToken = authTokenIssuer.issueTokens(person).refreshToken();
        Callable<AuthTokens> refresh = () -> refreshTokenUseCase.refresh(refreshToken);

        List<Outcome<AuthTokens>> outcomes = ConcurrentRace.run(List.of(refresh, refresh));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .singleElement()
                .satisfies(loser -> assertThat(loser.failure()).isInstanceOf(InvalidRefreshTokenException.class));
        assertThat(refreshTokenRepository.findByPersonAndRevokedFalseAndExpiresAtAfter(
                person, java.time.LocalDateTime.now().minusDays(1))).hasSize(1);
    }
}

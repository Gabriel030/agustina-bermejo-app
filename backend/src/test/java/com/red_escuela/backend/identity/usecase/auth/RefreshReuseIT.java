package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.AuthTokens;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RefreshReuseIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;
    @Autowired
    private LogoutUseCase logoutUseCase;
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
                person, LocalDateTime.now().minusDays(1)).size();
    }

    @Test
    void reusingRotatedRefreshTokenRevokesTheSuccessorAndEveryOtherSession() {
        Person person = persistPerson("reuse-rotated-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        String otherDevice = authTokenIssuer.issueTokens(person).refreshToken();
        AuthTokens rotated = refreshTokenUseCase.refresh(tokenA);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(rotated.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(otherDevice))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reusingRefreshTokenClosedByLogoutRevokesAllSessionsOfThePerson() {
        Person person = persistPerson("reuse-logout-" + System.nanoTime());
        String closedByLogout = authTokenIssuer.issueTokens(person).refreshToken();
        authTokenIssuer.issueTokens(person);
        logoutUseCase.logout(closedByLogout);
        assertThat(activeSessions(person)).isEqualTo(1);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(closedByLogout))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }
}

package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginWithGoogleServiceTest {
    private static final String ID_TOKEN = "google-id-token";
    private static final GoogleIdTokenService.GoogleProfile PROFILE =
            new GoogleIdTokenService.GoogleProfile("google-sub", "alumno@example.com", "Lucía", "Fernández");

    private final GoogleIdTokenService googleIdTokenService = mock(GoogleIdTokenService.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final AuthTokenIssuer authTokenIssuer = mock(AuthTokenIssuer.class);
    private final LoginWithGoogleService service =
            new LoginWithGoogleService(googleIdTokenService, personRepository, authTokenIssuer);

    private Person personWithStatus(StatusType status) {
        Person person = new Person();
        person.setEmail(PROFILE.email());
        person.setStatus(status);
        when(googleIdTokenService.verify(ID_TOKEN)).thenReturn(PROFILE);
        when(personRepository.findByGoogleSubjectId(PROFILE.subject())).thenReturn(Optional.of(person));
        return person;
    }

    @Test
    void anActivePersonLogsInWithGoogle() {
        Person person = personWithStatus(StatusType.ACTIVE);
        AuthTokens tokens = mock(AuthTokens.class);
        when(authTokenIssuer.issueTokens(person)).thenReturn(tokens);

        assertThat(service.login(ID_TOKEN)).isSameAs(tokens);
    }

    @ParameterizedTest
    @EnumSource(value = StatusType.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void aPersonThatIsNotActiveCannotLogInWithGoogle(StatusType status) {
        personWithStatus(status);

        assertThatThrownBy(() -> service.login(ID_TOKEN)).isInstanceOf(InvalidCredentialsException.class);
        verify(authTokenIssuer, never()).issueTokens(any());
    }

    @Test
    void aLockedPersonCannotLogInWithGoogle() {
        Person person = personWithStatus(StatusType.ACTIVE);
        person.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        assertThatThrownBy(() -> service.login(ID_TOKEN)).isInstanceOf(InvalidCredentialsException.class);
        verify(authTokenIssuer, never()).issueTokens(any());
    }

    @Test
    void anInactivePersonFoundByEmailIsNotLinkedToTheGoogleAccount() {
        Person person = new Person();
        person.setEmail(PROFILE.email());
        person.setStatus(StatusType.INACTIVE);
        when(googleIdTokenService.verify(ID_TOKEN)).thenReturn(PROFILE);
        when(personRepository.findByGoogleSubjectId(PROFILE.subject())).thenReturn(Optional.empty());
        when(personRepository.findByEmail(EmailAddress.of(PROFILE.email()))).thenReturn(Optional.of(person));

        assertThatThrownBy(() -> service.login(ID_TOKEN)).isInstanceOf(InvalidCredentialsException.class);
        assertThat(person.getGoogleSubjectId()).isNull();
        verify(personRepository, never()).save(any());
    }
}

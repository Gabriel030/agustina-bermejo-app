package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.service.DummyPasswordHash;
import com.red_escuela.backend.identity.repository.LoginAttemptRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginTimingTest {

    private static final String DUMMY_HASH = "dummy-hash";

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final DummyPasswordHash dummyPasswordHash = dummyHash();
    private final LoginService service = new LoginService(personRepository, mock(LoginAttemptRepository.class),
            passwordEncoder, mock(AuthTokenIssuer.class), dummyPasswordHash, 5, 15);

    private DummyPasswordHash dummyHash() {
        DummyPasswordHash hash = mock(DummyPasswordHash.class);
        when(hash.value()).thenReturn(DUMMY_HASH);
        return hash;
    }

    @Test
    void unknownUserStillRunsOnePasswordComparison() {
        when(personRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(personRepository.findByEmail(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(personRepository.findByDocumentTypeAndDni(org.mockito.ArgumentMatchers.any(), eq("ghost")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand("ghost", "pw", "1.1.1.1")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, times(1)).matches("pw", DUMMY_HASH);
    }

    @Test
    void lockedAccountStillRunsOnePasswordComparison() {
        Person person = new Person();
        person.setStatus(StatusType.ACTIVE);
        person.setPassword("real-hash");
        person.setLockedUntil(ApplicationTime.now().plusMinutes(10));
        when(personRepository.findByUsername("locked")).thenReturn(Optional.of(person));

        assertThatThrownBy(() -> service.login(new LoginCommand("locked", "pw", "1.1.1.1")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, times(1)).matches("pw", DUMMY_HASH);
    }

    @Test
    void inactiveAccountStillRunsOnePasswordComparison() {
        Person person = new Person();
        person.setStatus(StatusType.INACTIVE);
        person.setPassword("real-hash");
        when(personRepository.findByUsername("inactive")).thenReturn(Optional.of(person));

        assertThatThrownBy(() -> service.login(new LoginCommand("inactive", "pw", "1.1.1.1")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, times(1)).matches("pw", DUMMY_HASH);
    }
}

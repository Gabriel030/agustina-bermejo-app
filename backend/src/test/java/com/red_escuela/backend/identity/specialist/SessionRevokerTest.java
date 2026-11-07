package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRevokerTest {

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final SessionRevoker sessionRevoker = new SessionRevoker(refreshTokenRepository);

    @Test
    void marksEveryRefreshTokenOfThePersonAsRevoked() {
        Person person = mock(Person.class);
        RefreshToken first = new RefreshToken();
        RefreshToken second = new RefreshToken();
        assertThat(first.getRevoked()).isFalse();
        assertThat(second.getRevoked()).isFalse();
        List<RefreshToken> tokens = List.of(first, second);
        when(refreshTokenRepository.findByPerson(person)).thenReturn(tokens);

        sessionRevoker.revokeAllSessions(person);

        assertThat(first.getRevoked()).isTrue();
        assertThat(second.getRevoked()).isTrue();
        verify(refreshTokenRepository).saveAll(tokens);
    }
}

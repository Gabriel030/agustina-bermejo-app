package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevokeSessionByIdServiceTest {

    private static final Long SESSION_ID = 9L;

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RevokeSessionByIdService service = new RevokeSessionByIdService(refreshTokenRepository);

    private Person person(Long id) {
        Person person = new Person();
        person.setId(id);
        return person;
    }

    private RefreshToken tokenOwnedBy(Person owner) {
        RefreshToken token = new RefreshToken();
        token.setId(SESSION_ID);
        token.setPerson(owner);
        token.setRevoked(false);
        when(refreshTokenRepository.findById(SESSION_ID)).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void ownerRevokesTheirSession() {
        Person owner = person(1L);
        RefreshToken token = tokenOwnedBy(owner);

        service.revokeById(SESSION_ID, owner);

        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void aPersonCannotRevokeASessionOfAnotherPerson() {
        RefreshToken token = tokenOwnedBy(person(1L));

        assertThatThrownBy(() -> service.revokeById(SESSION_ID, person(2L)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(token.getRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokingAnUnknownSessionFailsWithInvalidRefreshToken() {
        when(refreshTokenRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeById(SESSION_ID, person(1L)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokingAnAlreadyRevokedSessionKeepsItRevoked() {
        Person owner = person(1L);
        RefreshToken token = tokenOwnedBy(owner);
        token.setRevoked(true);

        service.revokeById(SESSION_ID, owner);

        assertThat(token.getRevoked()).isTrue();
    }
}

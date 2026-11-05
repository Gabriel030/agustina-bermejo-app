package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.SessionRevoker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeSessionByIdService implements RevokeSessionByIdUseCase {

    private final SessionRevoker sessionRevoker;

    public RevokeSessionByIdService(SessionRevoker sessionRevoker) {
        this.sessionRevoker = sessionRevoker;
    }

    @Override
    public void revokeById(Long sessionId, Person person) {
        sessionRevoker.revokeSession(sessionId, person);
    }
}

package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeAllSessionsService implements RevokeAllSessionsUseCase {

    private final SessionRevoker sessionRevoker;

    public RevokeAllSessionsService(SessionRevoker sessionRevoker) {
        this.sessionRevoker = sessionRevoker;
    }

    @Override
    public void revokeAll(Person person) {
        sessionRevoker.revokeAllSessions(person);
    }
}

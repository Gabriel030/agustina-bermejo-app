package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;

public interface RevokeSessionByIdUseCase {
    void revokeById(Long sessionId, Person person);
}

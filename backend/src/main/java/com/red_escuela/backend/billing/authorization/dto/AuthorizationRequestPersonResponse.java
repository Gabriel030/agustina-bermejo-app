package com.red_escuela.backend.billing.authorization.dto;

import com.red_escuela.backend.identity.entity.Person;

import java.util.List;
import java.util.Map;

public record AuthorizationRequestPersonResponse(Long id, String name, List<String> roles) {

    public static AuthorizationRequestPersonResponse of(Person person, Map<Long, String> names, List<String> roles) {
        return person == null ? null : new AuthorizationRequestPersonResponse(person.getId(), names.get(person.getId()), roles);
    }
}

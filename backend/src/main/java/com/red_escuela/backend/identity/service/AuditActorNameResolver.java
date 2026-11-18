package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

@Service
public class AuditActorNameResolver {

    private final PersonNameFinder personNameFinder;

    public AuditActorNameResolver(PersonNameFinder personNameFinder) {
        this.personNameFinder = personNameFinder;
    }

    public Map<Long, String> resolve(Collection<Long> personIds) {
        return personNameFinder.findFullNamesByPersonId(personIds);
    }
}

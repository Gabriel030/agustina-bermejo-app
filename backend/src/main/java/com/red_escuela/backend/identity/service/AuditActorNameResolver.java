package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class AuditActorNameResolver {

    private final PersonNameFinder personNameFinder;

    public AuditActorNameResolver(PersonNameFinder personNameFinder) {
        this.personNameFinder = personNameFinder;
    }

    public List<Long> findPersonIdsByName(String term, int limit) {
        return personNameFinder.findInstitutePersonIdsByNameContaining(term, limit);
    }

    public Map<Long, String> resolve(Collection<Long> personIds) {
        return personNameFinder.findFullNamesByPersonId(personIds);
    }
}

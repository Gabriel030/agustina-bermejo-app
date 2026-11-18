package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

@Service
public class PlatformAuditActorNameResolver {

    private final PersonNameFinder personNameFinder;

    public PlatformAuditActorNameResolver(PersonNameFinder personNameFinder) {
        this.personNameFinder = personNameFinder;
    }

    public Map<Long, String> resolve(Collection<Long> personIds) {
        return personNameFinder.findFullNamesByPersonId(personIds);
    }
}

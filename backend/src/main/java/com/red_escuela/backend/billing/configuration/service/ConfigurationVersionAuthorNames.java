package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.identity.api.PersonNameLookup;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ConfigurationVersionAuthorNames {
    private final PersonNameLookup personNames;

    public ConfigurationVersionAuthorNames(PersonNameLookup personNames) {
        this.personNames = personNames;
    }

    public Map<Long, String> findNamesOf(Collection<ConfigurationVersion> versions) {
        Set<Long> personIds = new LinkedHashSet<>();
        for (ConfigurationVersion version : versions) {
            personIds.add(version.getProposedById());
            personIds.add(version.getDecidedById());
        }
        personIds.removeIf(Objects::isNull);
        return personNames.findFullNamesByPersonId(personIds);
    }
}

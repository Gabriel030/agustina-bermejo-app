package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.platform.domain.ActorSummary;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ActorSummaryFinder {

    private final PersonNameFinder personNameFinder;

    public ActorSummaryFinder(PersonNameFinder personNameFinder) {
        this.personNameFinder = personNameFinder;
    }

    public ActorSummary find(Long personId) {
        if (personId == null) {
            return null;
        }
        return findAll(List.of(personId)).get(personId);
    }

    public Map<Long, ActorSummary> findAll(Collection<Long> personIds) {
        List<Long> ids = personIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(ids);
        Map<Long, ActorSummary> summaries = new HashMap<>();
        for (Long id : ids) {
            summaries.put(id, new ActorSummary(id, names.get(id)));
        }
        return summaries;
    }
}

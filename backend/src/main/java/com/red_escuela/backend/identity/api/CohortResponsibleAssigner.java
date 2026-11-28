package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.CohortResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.PreceptorRoleRequiredException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CohortResponsibleAssigner {

    private static final String PRECEPTOR_ROLE = "PRECEPTOR";

    private final PreceptorAssignmentRepository preceptorAssignmentRepository;
    private final PersonRepository personRepository;

    public CohortResponsibleAssigner(PreceptorAssignmentRepository preceptorAssignmentRepository, PersonRepository personRepository) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public void assign(Long programCohortId, Collection<Long> personIds, Long actorId) {
        Set<Long> distinctIds = personIds == null ? Set.of() : new LinkedHashSet<>(personIds);
        if (distinctIds.isEmpty()) {
            throw new CohortResponsibleRequiredException();
        }
        Map<Long, Person> peopleById = personRepository.findAllWithRolesByIdIn(distinctIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        for (Long personId : distinctIds) {
            Person person = peopleById.get(personId);
            boolean isPreceptor = person != null && person.getRoles().stream().anyMatch(role -> PRECEPTOR_ROLE.equals(role.getName()));
            if (!isPreceptor) {
                throw new PreceptorRoleRequiredException(personId);
            }
        }
        for (Long personId : distinctIds) {
            if (!preceptorAssignmentRepository.existsByPersonIdAndProgramCohortId(personId, programCohortId)) {
                preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).programCohortId(programCohortId).createdBy(actorId).build());
            }
        }
    }
}

package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class SectionPreceptorFinder {

    private final PreceptorAssignmentRepository preceptorAssignmentRepository;
    private final PersonRepository personRepository;

    public SectionPreceptorFinder(PreceptorAssignmentRepository preceptorAssignmentRepository,
                                  PersonRepository personRepository) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.personRepository = personRepository;
    }

    public Map<Long, List<SectionPreceptorView>> findBySubjectSectionIds(Collection<Long> subjectSectionIds) {
        if (subjectSectionIds.isEmpty()) {
            return Map.of();
        }
        return groupBySection(preceptorAssignmentRepository.findBySubjectSectionIdIn(subjectSectionIds),
                PreceptorAssignment::getSubjectSectionId);
    }

    public Map<Long, List<SectionPreceptorView>> findByCourseSectionIds(Collection<Long> courseSectionIds) {
        if (courseSectionIds.isEmpty()) {
            return Map.of();
        }
        return groupBySection(preceptorAssignmentRepository.findByCourseSectionIdIn(courseSectionIds),
                PreceptorAssignment::getCourseSectionId);
    }

    private Map<Long, List<SectionPreceptorView>> groupBySection(List<PreceptorAssignment> assignments,
                                                                 Function<PreceptorAssignment, Long> sectionOf) {
        if (assignments.isEmpty()) {
            return Map.of();
        }
        Map<Long, Person> peopleById = personRepository
                .findAllById(assignments.stream().map(PreceptorAssignment::getPersonId).distinct().toList()).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        return assignments.stream()
                .sorted(Comparator.comparing(PreceptorAssignment::getId))
                .collect(Collectors.groupingBy(sectionOf, Collectors.mapping(
                        assignment -> toView(assignment, peopleById.get(assignment.getPersonId())),
                        Collectors.toList())));
    }

    private SectionPreceptorView toView(PreceptorAssignment assignment, Person person) {
        return new SectionPreceptorView(assignment.getId(), assignment.getPersonId(),
                person.getName() + " " + person.getLastname());
    }
}

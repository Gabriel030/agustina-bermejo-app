package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentTargetInvalidException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.identity.dto.ListPreceptorAssignmentsCommand;
import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ListPreceptorAssignmentsTransaction {

    private final PreceptorAssignmentRepository preceptorAssignmentRepository;
    private final PersonRepository personRepository;

    public ListPreceptorAssignmentsTransaction(PreceptorAssignmentRepository preceptorAssignmentRepository,
                                               PersonRepository personRepository) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public List<PreceptorAssignmentResult> list(ListPreceptorAssignmentsCommand command) {
        if ((command.subjectSectionId() == null) == (command.courseSectionId() == null)) {
            throw new PreceptorAssignmentTargetInvalidException();
        }
        List<PreceptorAssignment> assignments = command.subjectSectionId() != null
                ? preceptorAssignmentRepository.findBySubjectSectionIdOrderByIdAsc(command.subjectSectionId())
                : preceptorAssignmentRepository.findByCourseSectionIdOrderByIdAsc(command.courseSectionId());
        Map<Long, Person> peopleById = personRepository
                .findAllById(assignments.stream().map(PreceptorAssignment::getPersonId).toList()).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        return assignments.stream()
                .map(assignment -> new PreceptorAssignmentResult(assignment, peopleById.get(assignment.getPersonId())))
                .toList();
    }
}

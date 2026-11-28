package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.CohortResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.CourseResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.PreceptorRoleRequiredException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
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
    private final IdentityAuditRecorder identityAuditRecorder;

    public CohortResponsibleAssigner(PreceptorAssignmentRepository preceptorAssignmentRepository, PersonRepository personRepository,
                                     IdentityAuditRecorder identityAuditRecorder) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.personRepository = personRepository;
        this.identityAuditRecorder = identityAuditRecorder;
    }

    @Transactional
    public Set<Long> replace(Long programCohortId, Collection<Long> personIds, Long actorId) {
        Set<Long> distinctIds = personIds == null ? Set.of() : new LinkedHashSet<>(personIds);
        if (distinctIds.isEmpty()) {
            throw new CohortResponsibleRequiredException();
        }
        requirePreceptors(distinctIds);
        List<PreceptorAssignment> current = preceptorAssignmentRepository.findByProgramCohortId(programCohortId);
        for (PreceptorAssignment assignment : current) {
            if (!distinctIds.contains(assignment.getPersonId())) {
                preceptorAssignmentRepository.delete(assignment);
                identityAuditRecorder.recordPreceptorUnassigned(assignment);
            }
        }
        Set<Long> alreadyAssigned = current.stream().map(PreceptorAssignment::getPersonId).collect(Collectors.toSet());
        for (Long personId : distinctIds) {
            if (!alreadyAssigned.contains(personId)) {
                PreceptorAssignment saved = preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).programCohortId(programCohortId).createdBy(actorId).build());
                identityAuditRecorder.recordPreceptorAssigned(saved);
            }
        }
        return distinctIds;
    }

    @Transactional
    public void assign(Long programCohortId, Collection<Long> personIds, Long actorId) {
        Set<Long> distinctIds = personIds == null ? Set.of() : new LinkedHashSet<>(personIds);
        if (distinctIds.isEmpty()) {
            throw new CohortResponsibleRequiredException();
        }
        requirePreceptors(distinctIds);
        for (Long personId : distinctIds) {
            if (!preceptorAssignmentRepository.existsByPersonIdAndProgramCohortId(personId, programCohortId)) {
                preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).programCohortId(programCohortId).createdBy(actorId).build());
            }
        }
    }

    @Transactional
    public void assignToCourse(Long courseId, Collection<Long> personIds, Long actorId) {
        Set<Long> distinctIds = personIds == null ? Set.of() : new LinkedHashSet<>(personIds);
        if (distinctIds.isEmpty()) {
            throw new CourseResponsibleRequiredException();
        }
        requirePreceptors(distinctIds);
        for (Long personId : distinctIds) {
            if (!preceptorAssignmentRepository.existsByPersonIdAndCourseId(personId, courseId)) {
                preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).courseId(courseId).createdBy(actorId).build());
            }
        }
    }

    @Transactional
    public void assignToSubjectSection(Long subjectSectionId, Collection<Long> personIds, Long actorId) {
        Set<Long> distinctIds = personIds == null ? Set.of() : new LinkedHashSet<>(personIds);
        requirePreceptors(distinctIds);
        for (Long personId : distinctIds) {
            if (!preceptorAssignmentRepository.existsByPersonIdAndSubjectSectionId(personId, subjectSectionId)) {
                preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).subjectSectionId(subjectSectionId).createdBy(actorId).build());
            }
        }
    }

    @Transactional
    public void assignToCourseSection(Long courseSectionId, Collection<Long> personIds, Long actorId) {
        Set<Long> distinctIds = personIds == null ? Set.of() : new LinkedHashSet<>(personIds);
        requirePreceptors(distinctIds);
        for (Long personId : distinctIds) {
            if (!preceptorAssignmentRepository.existsByPersonIdAndCourseSectionId(personId, courseSectionId)) {
                preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).courseSectionId(courseSectionId).createdBy(actorId).build());
            }
        }
    }

    private void requirePreceptors(Set<Long> distinctIds) {
        Map<Long, Person> peopleById = personRepository.findAllWithRolesByIdIn(distinctIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        for (Long personId : distinctIds) {
            Person person = peopleById.get(personId);
            boolean isPreceptor = person != null && person.getRoles().stream().anyMatch(role -> PRECEPTOR_ROLE.equals(role.getName()));
            if (!isPreceptor) {
                throw new PreceptorRoleRequiredException(personId);
            }
        }
    }
}

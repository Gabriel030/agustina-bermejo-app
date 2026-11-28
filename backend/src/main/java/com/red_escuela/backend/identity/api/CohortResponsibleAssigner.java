package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.CohortResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.CourseResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.PreceptorRoleRequiredException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
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
    private final AuditTrail auditTrail;

    public CohortResponsibleAssigner(PreceptorAssignmentRepository preceptorAssignmentRepository, PersonRepository personRepository,
                                     AuditTrail auditTrail) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.personRepository = personRepository;
        this.auditTrail = auditTrail;
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
                auditTrail.record(new AuditRecord(AuditAction.PRECEPTOR_UNASSIGNED, AuditEntityType.PRECEPTOR_ASSIGNMENT,
                        assignment.getId(), describe(assignment), null, null));
            }
        }
        Set<Long> alreadyAssigned = current.stream().map(PreceptorAssignment::getPersonId).collect(Collectors.toSet());
        for (Long personId : distinctIds) {
            if (!alreadyAssigned.contains(personId)) {
                PreceptorAssignment saved = preceptorAssignmentRepository.save(PreceptorAssignment.builder()
                        .personId(personId).programCohortId(programCohortId).createdBy(actorId).build());
                auditTrail.record(new AuditRecord(AuditAction.PRECEPTOR_ASSIGNED, AuditEntityType.PRECEPTOR_ASSIGNMENT,
                        saved.getId(), null, describe(saved), null));
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

    private String describe(PreceptorAssignment assignment) {
        return "personId=" + assignment.getPersonId() + ";programCohortId=" + assignment.getProgramCohortId();
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

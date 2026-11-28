package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.PreceptorAssignmentTargetPort;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentAlreadyExistsException;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentSectionNotFoundException;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentTargetInvalidException;
import com.red_escuela.backend.identity.exception.PreceptorRoleRequiredException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.usecase.preceptorassignment.AssignPreceptorCommand;
import com.red_escuela.backend.identity.usecase.preceptorassignment.PreceptorAssignmentResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssignPreceptorTransaction {

    private static final String PRECEPTOR_ROLE = "PRECEPTOR";

    private final PersonRepository personRepository;
    private final PreceptorAssignmentRepository preceptorAssignmentRepository;
    private final PreceptorAssignmentTargetPort preceptorAssignmentTargetPort;
    private final IdentityAuditRecorder identityAuditRecorder;

    public AssignPreceptorTransaction(PersonRepository personRepository,
                                      PreceptorAssignmentRepository preceptorAssignmentRepository,
                                      PreceptorAssignmentTargetPort preceptorAssignmentTargetPort,
                                      IdentityAuditRecorder identityAuditRecorder) {
        this.personRepository = personRepository;
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.preceptorAssignmentTargetPort = preceptorAssignmentTargetPort;
        this.identityAuditRecorder = identityAuditRecorder;
    }

    @Transactional
    public PreceptorAssignmentResult assign(AssignPreceptorCommand command) {
        if ((command.subjectSectionId() == null) == (command.courseSectionId() == null)) {
            throw new PreceptorAssignmentTargetInvalidException();
        }
        requireSectionExists(command);
        Person person = personRepository.findAllWithRolesByIdIn(List.of(command.personId())).stream()
                .findFirst()
                .orElseThrow(() -> new PersonNotFoundException(command.personId()));
        if (person.getRoles().stream().noneMatch(role -> PRECEPTOR_ROLE.equals(role.getName()))) {
            throw new PreceptorRoleRequiredException(command.personId());
        }
        requireNotAssigned(command);
        PreceptorAssignment assignment = saveAssignment(command);
        identityAuditRecorder.recordPreceptorAssigned(assignment);
        return new PreceptorAssignmentResult(assignment, person);
    }

    private void requireSectionExists(AssignPreceptorCommand command) {
        if (command.subjectSectionId() != null
                && !preceptorAssignmentTargetPort.subjectSectionExists(command.subjectSectionId())) {
            throw new PreceptorAssignmentSectionNotFoundException(command.subjectSectionId());
        }
        if (command.courseSectionId() != null
                && !preceptorAssignmentTargetPort.courseSectionExists(command.courseSectionId())) {
            throw new PreceptorAssignmentSectionNotFoundException(command.courseSectionId());
        }
    }

    private void requireNotAssigned(AssignPreceptorCommand command) {
        boolean exists = command.subjectSectionId() != null
                ? preceptorAssignmentRepository.existsByPersonIdAndSubjectSectionId(command.personId(), command.subjectSectionId())
                : preceptorAssignmentRepository.existsByPersonIdAndCourseSectionId(command.personId(), command.courseSectionId());
        if (exists) {
            throw new PreceptorAssignmentAlreadyExistsException(command.personId());
        }
    }

    private PreceptorAssignment saveAssignment(AssignPreceptorCommand command) {
        try {
            return preceptorAssignmentRepository.saveAndFlush(PreceptorAssignment.builder()
                    .personId(command.personId())
                    .subjectSectionId(command.subjectSectionId())
                    .courseSectionId(command.courseSectionId())
                    .createdBy(command.actorId())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new PreceptorAssignmentAlreadyExistsException(command.personId());
        }
    }
}

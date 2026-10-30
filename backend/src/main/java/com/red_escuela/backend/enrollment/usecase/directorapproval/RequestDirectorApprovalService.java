package com.red_escuela.backend.enrollment.usecase.directorapproval;

import java.time.Clock;
import com.red_escuela.backend.enrollment.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalRequestAlreadyActiveException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.ProgramPlanSubjectGuard;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@UseCase
public class RequestDirectorApprovalService implements RequestDirectorApprovalUseCase {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final ProgramPlanSubjectGuard programPlanSubjectGuard;
    private final CurrentPersonService currentPersonService;
    private final Clock clock;

    public RequestDirectorApprovalService(DirectorApprovalRequestRepository directorApprovalRequestRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           SubjectRepository subjectRepository,
                                           ProgramPlanSubjectGuard programPlanSubjectGuard,
                                           CurrentPersonService currentPersonService,
                                           Clock clock) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.programPlanSubjectGuard = programPlanSubjectGuard;
        this.currentPersonService = currentPersonService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DirectorApprovalRequest request(CreateDirectorApprovalRequestRequest request) {
        ProgramEnrollment programEnrollment = programEnrollmentRepository.findById(request.programEnrollmentId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(request.programEnrollmentId()));
        Subject subject = null;
        if (request.subjectId() != null) {
            subject = subjectRepository.findById(request.subjectId())
                    .orElseThrow(() -> new SubjectNotFoundException(request.subjectId()));
            requireSubjectInProgramPlan(programEnrollment, subject);
        }

        DirectorApprovalRequest entity = DirectorApprovalRequest.open(
                request.type(), programEnrollment, subject, request.justification(),
                currentPersonService.resolveCurrentPerson(), LocalDateTime.now(clock));

        boolean alreadyActive = directorApprovalRequestRepository
                .findByProgramEnrollmentIdAndTypeAndStatus(
                        request.programEnrollmentId(), request.type(), DirectorApprovalRequestStatus.REQUESTED)
                .stream()
                .anyMatch(existing -> Objects.equals(
                        existing.getSubject() != null ? existing.getSubject().getId() : null, request.subjectId()));
        if (alreadyActive) {
            throw new DirectorApprovalRequestAlreadyActiveException(request.programEnrollmentId());
        }
        return directorApprovalRequestRepository.save(entity);
    }

    private void requireSubjectInProgramPlan(ProgramEnrollment programEnrollment, Subject subject) {
        programPlanSubjectGuard.requireSubjectInPlan(programEnrollment.getProgram().getId(), subject.getId());
    }
}

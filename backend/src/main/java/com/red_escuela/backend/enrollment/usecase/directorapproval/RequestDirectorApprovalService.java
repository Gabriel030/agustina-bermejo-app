package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalRequestAlreadyActiveException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
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
    private final CurrentPersonService currentPersonService;

    public RequestDirectorApprovalService(DirectorApprovalRequestRepository directorApprovalRequestRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           SubjectRepository subjectRepository,
                                           CurrentPersonService currentPersonService) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.currentPersonService = currentPersonService;
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
        }

        boolean yaActiva = directorApprovalRequestRepository
                .findByProgramEnrollmentIdAndTypeAndStatus(
                        request.programEnrollmentId(), request.type(), DirectorApprovalRequestStatus.REQUESTED)
                .stream()
                .anyMatch(existing -> Objects.equals(
                        existing.getSubject() != null ? existing.getSubject().getId() : null, request.subjectId()));
        if (yaActiva) {
            throw new DirectorApprovalRequestAlreadyActiveException(request.programEnrollmentId());
        }

        DirectorApprovalRequest entity = new DirectorApprovalRequest();
        entity.setType(request.type());
        entity.setProgramEnrollment(programEnrollment);
        entity.setSubject(subject);
        entity.setJustification(request.justification());
        entity.setStatus(DirectorApprovalRequestStatus.REQUESTED);
        entity.setRequestedBy(currentPersonService.resolveCurrentPerson());
        entity.setRequestedAt(LocalDateTime.now());
        return directorApprovalRequestRepository.save(entity);
    }
}

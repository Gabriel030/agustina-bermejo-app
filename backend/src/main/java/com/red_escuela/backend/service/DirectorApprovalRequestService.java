package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.entity.DirectorApprovalRequest;
import com.red_escuela.backend.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.DirectorApprovalRequestNotFoundException;
import com.red_escuela.backend.exception.InvalidDirectorApprovalRequestStateException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-DI-03: excepciones del Director (LATE_EXAM, PREREQUISITE_WAIVER), catálogo abierto
 * (decisión 12). Esta fase SOLO implementa el flujo de solicitud/aprobación/rechazo — no
 * dispara ningún efecto sobre ExamBoardRegistrationService/SubjectSectionEnrollmentService,
 * que es deuda técnica documentada explícitamente en la spec (no se improvisa el cableado acá).
 */
@Service
public class DirectorApprovalRequestService {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final CurrentPersonService currentPersonService;

    public DirectorApprovalRequestService(
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramEnrollmentRepository programEnrollmentRepository, SubjectRepository subjectRepository,
            CurrentPersonService currentPersonService) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public DirectorApprovalRequest solicitar(CreateDirectorApprovalRequestRequest request) {
        ProgramEnrollment programEnrollment = programEnrollmentRepository.findById(request.programEnrollmentId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(request.programEnrollmentId()));
        Subject subject = null;
        if (request.subjectId() != null) {
            subject = subjectRepository.findById(request.subjectId())
                    .orElseThrow(() -> new SubjectNotFoundException(request.subjectId()));
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

    @Transactional
    public DirectorApprovalRequest aprobar(Long id, String notas) {
        return decidir(id, DirectorApprovalRequestStatus.APPROVED, notas);
    }

    @Transactional
    public DirectorApprovalRequest rechazar(Long id, String notas) {
        return decidir(id, DirectorApprovalRequestStatus.REJECTED, notas);
    }

    private DirectorApprovalRequest decidir(Long id, DirectorApprovalRequestStatus nuevoStatus, String notas) {
        DirectorApprovalRequest entity = findOrThrow(id);
        if (entity.getStatus() != DirectorApprovalRequestStatus.REQUESTED) {
            throw new InvalidDirectorApprovalRequestStateException(id);
        }
        entity.setStatus(nuevoStatus);
        entity.setDecidedBy(currentPersonService.resolveCurrentPerson());
        entity.setDecidedAt(LocalDateTime.now());
        entity.setDecisionNotes(notas);
        return directorApprovalRequestRepository.save(entity);
    }

    public List<DirectorApprovalRequest> listarPendientes() {
        return directorApprovalRequestRepository.findByStatus(DirectorApprovalRequestStatus.REQUESTED);
    }

    public List<DirectorApprovalRequest> listarPorProgramEnrollment(Long programEnrollmentId) {
        return directorApprovalRequestRepository.findByProgramEnrollmentId(programEnrollmentId);
    }

    private DirectorApprovalRequest findOrThrow(Long id) {
        return directorApprovalRequestRepository.findById(id)
                .orElseThrow(() -> new DirectorApprovalRequestNotFoundException(id));
    }
}

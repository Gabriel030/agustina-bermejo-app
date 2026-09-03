package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.entity.DirectorApprovalRequest;
import com.red_escuela.backend.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.DirectorApprovalRequestAlreadyActiveException;
import com.red_escuela.backend.exception.DirectorApprovalRequestNotFoundException;
import com.red_escuela.backend.exception.InvalidDirectorApprovalRequestStateException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.notification.DirectorApprovalRequestApprovedEvent;
import com.red_escuela.backend.notification.DirectorApprovalRequestRejectedEvent;
import com.red_escuela.backend.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
    private final ApplicationEventPublisher eventPublisher;

    public DirectorApprovalRequestService(
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramEnrollmentRepository programEnrollmentRepository, SubjectRepository subjectRepository,
            CurrentPersonService currentPersonService, ApplicationEventPublisher eventPublisher) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectRepository = subjectRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
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

        // Un mismo alumno puede tener a la vez excepciones activas de tipos distintos (o del
        // mismo tipo sobre materias distintas) — solo se bloquea el duplicado exacto: mismo
        // tipo + misma materia (o ninguna) + mismo trámite todavía REQUESTED.
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
        DirectorApprovalRequest saved = directorApprovalRequestRepository.save(entity);

        if (nuevoStatus == DirectorApprovalRequestStatus.APPROVED) {
            eventPublisher.publishEvent(new DirectorApprovalRequestApprovedEvent(saved, saved.getRequestedBy()));
        } else {
            eventPublisher.publishEvent(new DirectorApprovalRequestRejectedEvent(saved, saved.getRequestedBy()));
        }
        return saved;
    }

    public List<DirectorApprovalRequest> listar(DirectorApprovalRequestStatus status) {
        return directorApprovalRequestRepository.findByStatus(status != null ? status : DirectorApprovalRequestStatus.REQUESTED);
    }

    public List<DirectorApprovalRequest> listarPorProgramEnrollment(Long programEnrollmentId) {
        return directorApprovalRequestRepository.findByProgramEnrollmentId(programEnrollmentId);
    }

    public DirectorApprovalRequest verDetalle(Long id) {
        return findOrThrow(id);
    }

    private DirectorApprovalRequest findOrThrow(Long id) {
        return directorApprovalRequestRepository.findById(id)
                .orElseThrow(() -> new DirectorApprovalRequestNotFoundException(id));
    }
}

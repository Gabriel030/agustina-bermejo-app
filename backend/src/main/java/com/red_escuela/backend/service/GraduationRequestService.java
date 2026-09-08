package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.GraduationRequest;
import com.red_escuela.backend.entity.GraduationRequestStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.exception.GraduationRequestNotFoundException;
import com.red_escuela.backend.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.notification.GraduationRequestApprovedEvent;
import com.red_escuela.backend.notification.GraduationRequestRejectedEvent;
import com.red_escuela.backend.repository.GraduationRequestRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.billing.service.InvoiceQueryService;

/**
 * RF-AL-15/RF-DI-05: flujo de egreso con estados explícitos (decisión 8) — Alumno solicita,
 * Administrativo verifica deuda, Director aprueba. Reusa ProgramEnrollmentService.calcularProgreso
 * tal cual (elegibleParaCompletar) sin tocarlo, y InvoiceService.calcularDeuda para el paso de
 * verificación (InvoiceQueryService.tieneDeuda desde Fase 6.1, reemplaza al InvoiceService
 * viejo eliminado en Fase 6.0).
 */
@Service
public class GraduationRequestService {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final InvoiceQueryService invoiceQueryService;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;

    public GraduationRequestService(
            GraduationRequestRepository graduationRequestRepository, ProgramEnrollmentRepository programEnrollmentRepository,
            ProgramEnrollmentService programEnrollmentService, InvoiceQueryService invoiceQueryService,
            CurrentPersonService currentPersonService, ApplicationEventPublisher eventPublisher) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.invoiceQueryService = invoiceQueryService;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GraduationRequest solicitar(Long programEnrollmentId) {
        ProgramEnrollment programEnrollment = findProgramEnrollmentOrThrow(programEnrollmentId);
        if (!programEnrollmentService.calcularProgreso(programEnrollmentId).elegibleParaCompletar()) {
            throw new NotEligibleForGraduationException(programEnrollmentId);
        }

        List<GraduationRequest> activos = graduationRequestRepository
                .findByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED);
        if (!activos.isEmpty()) {
            throw new GraduationRequestAlreadyActiveException(programEnrollmentId);
        }

        GraduationRequest request = new GraduationRequest();
        request.setProgramEnrollment(programEnrollment);
        request.setStatus(GraduationRequestStatus.REQUESTED);
        request.setRequestedAt(LocalDateTime.now());
        try {
            return graduationRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new GraduationRequestAlreadyActiveException(programEnrollmentId);
        }
    }

    // Épica 3 Fase 4 (feedback de producto): el trámite de egreso deja de depender de que el
    // alumno lo pida a mano — se dispara solo apenas la matriculación queda elegible (ver
    // GradeItemService.cargarNotaProgramEnrollment, se llama al aprobarse una materia). Si ya
    // hay un trámite activo (no rechazado) no hace nada — mismo criterio de "un solo trámite
    // activo por matriculación" que ya aplica `solicitar`.
    @Transactional
    public void createGraduationRequestIfEligible(Long programEnrollmentId) {
        if (!programEnrollmentService.calcularProgreso(programEnrollmentId).elegibleParaCompletar()) {
            return;
        }
        try {
            solicitar(programEnrollmentId);
        } catch (GraduationRequestAlreadyActiveException e) {
            // Ya hay un trámite en curso para esta matriculación — nada que hacer.
        }
    }

    // Decisión 10: si hay deuda, el trámite NO se rechaza — sigue en REQUESTED hasta que se
    // vuelva a intentar. REJECTED se reserva para una decisión humana explícita.
    @Transactional
    public GraduationRequest verificarDeuda(Long graduationRequestId) {
        GraduationRequest request = findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.REQUESTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Long studentId = request.getProgramEnrollment().getStudent().getId();
        if (invoiceQueryService.tieneDeuda(studentId)) {
            throw new PendingDebtException(studentId);
        }

        request.setStatus(GraduationRequestStatus.ADMIN_VERIFIED);
        request.setVerifiedBy(currentPersonService.resolveCurrentPerson());
        request.setVerifiedAt(LocalDateTime.now());
        return graduationRequestRepository.save(request);
    }

    @Transactional
    public GraduationRequest aprobar(Long graduationRequestId) {
        GraduationRequest request = findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.ADMIN_VERIFIED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Person decidedBy = currentPersonService.resolveCurrentPerson();
        request.setStatus(GraduationRequestStatus.APPROVED);
        request.setDecidedBy(decidedBy);
        request.setDecidedAt(LocalDateTime.now());
        graduationRequestRepository.save(request);

        ProgramEnrollment programEnrollment = request.getProgramEnrollment();
        programEnrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
        programEnrollment.setCompletionDate(LocalDateTime.now());
        programEnrollmentRepository.save(programEnrollment);

        eventPublisher.publishEvent(new GraduationRequestApprovedEvent(request, programEnrollment.getStudent().getPerson()));
        return request;
    }

    @Transactional
    public GraduationRequest rechazar(Long graduationRequestId, String motivo) {
        GraduationRequest request = findOrThrow(graduationRequestId);
        if (request.getStatus() == GraduationRequestStatus.APPROVED || request.getStatus() == GraduationRequestStatus.REJECTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        request.setStatus(GraduationRequestStatus.REJECTED);
        request.setDecidedBy(currentPersonService.resolveCurrentPerson());
        request.setDecidedAt(LocalDateTime.now());
        request.setRejectionReason(motivo);
        GraduationRequest saved = graduationRequestRepository.save(request);

        eventPublisher.publishEvent(new GraduationRequestRejectedEvent(saved, saved.getProgramEnrollment().getStudent().getPerson()));
        return saved;
    }

    public List<GraduationRequest> listarPorProgramEnrollment(Long programEnrollmentId) {
        return graduationRequestRepository.findByProgramEnrollmentId(programEnrollmentId);
    }

    public List<GraduationRequest> listar(GraduationRequestStatus status) {
        return graduationRequestRepository.findByStatus(status);
    }

    public GraduationRequest verDetalle(Long id) {
        return findOrThrow(id);
    }

    private ProgramEnrollment findProgramEnrollmentOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }

    private GraduationRequest findOrThrow(Long id) {
        return graduationRequestRepository.findById(id)
                .orElseThrow(() -> new GraduationRequestNotFoundException(id));
    }
}

package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.DebtSummaryResponse;
import com.red_escuela.backend.entity.GraduationRequest;
import com.red_escuela.backend.entity.GraduationRequestStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.exception.GraduationRequestNotFoundException;
import com.red_escuela.backend.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.exception.PendingDebtException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.repository.GraduationRequestRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-AL-15/RF-DI-05: flujo de egreso con estados explícitos (decisión 8) — Alumno solicita,
 * Administrativo verifica deuda, Director aprueba. Reusa ProgramEnrollmentService.calcularProgreso
 * tal cual (elegibleParaCompletar) sin tocarlo, y InvoiceService.calcularDeuda para el paso de
 * verificación.
 */
@Service
public class GraduationRequestService {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final InvoiceService invoiceService;
    private final CurrentPersonService currentPersonService;

    public GraduationRequestService(
            GraduationRequestRepository graduationRequestRepository, ProgramEnrollmentRepository programEnrollmentRepository,
            ProgramEnrollmentService programEnrollmentService, InvoiceService invoiceService,
            CurrentPersonService currentPersonService) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.invoiceService = invoiceService;
        this.currentPersonService = currentPersonService;
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

    // Decisión 10: si hay deuda, el trámite NO se rechaza — sigue en REQUESTED hasta que se
    // vuelva a intentar. REJECTED se reserva para una decisión humana explícita.
    @Transactional
    public GraduationRequest verificarDeuda(Long graduationRequestId) {
        GraduationRequest request = findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.REQUESTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Long studentId = request.getProgramEnrollment().getStudent().getId();
        DebtSummaryResponse deuda = invoiceService.calcularDeuda(studentId);
        if (deuda.tieneDeuda()) {
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
        return graduationRequestRepository.save(request);
    }

    public List<GraduationRequest> listarPorProgramEnrollment(Long programEnrollmentId) {
        return graduationRequestRepository.findByProgramEnrollmentId(programEnrollmentId);
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

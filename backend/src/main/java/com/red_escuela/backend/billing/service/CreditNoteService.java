package com.red_escuela.backend.billing.service;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;

import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.entity.CreditApplication;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.CreditNoteStatus;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.exception.CreditNoteNotFoundException;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final StudentRepository studentRepository;
    private final CurrentPersonService currentPersonService;

    public CreditNoteService(CreditNoteRepository creditNoteRepository, StudentRepository studentRepository,
                              CurrentPersonService currentPersonService) {
        this.creditNoteRepository = creditNoteRepository;
        this.studentRepository = studentRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional(readOnly = true)
    public List<CreditNote> listForStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException(studentId);
        }
        return creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    // Se calcula, nunca se guarda como contador mutable (mismo criterio que
    // Invoice.getRemainingBalance()/InvoiceRepository.countByPaymentPlanSelectionAndFeeTypeAndPromotionBenefit).
    @Transactional(readOnly = true)
    public BigDecimal saldoDisponible(Long studentId) {
        return creditNoteRepository.findByStudentIdAndStatusNotOrderByCreatedAtAsc(studentId, CreditNoteStatus.CONSUMED).stream()
                .map(CreditNote::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public CreditNote createManual(Long studentId, CreateCreditNoteRequest request) {
        if (request.reasonType() != CreditNoteReasonType.ADMINISTRATIVE_CORRECTION && request.reasonType() != CreditNoteReasonType.OTHER) {
            throw new InvalidCreditNoteReasonException(request.reasonType());
        }
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        return createFor(student, request.amount(), request.reasonType(), request.reason(), currentPersonService.resolveCurrentPerson());
    }

    @Transactional
    public CreditNote createFor(Student student, BigDecimal amount, CreditNoteReasonType reasonType, String reason, Person createdBy) {
        CreditNote creditNote = new CreditNote();
        creditNote.setStudent(student);
        creditNote.setAmount(amount);
        creditNote.setReasonType(reasonType);
        creditNote.setReason(reason);
        creditNote.setCreatedBy(createdBy);
        return creditNoteRepository.save(creditNote);
    }

    // Consumo FIFO (notas más viejas primero) a través de tantas CreditNote como haga falta para
    // cubrir amount — un pago con CREDIT_BALANCE no elige una nota puntual, el crédito de un
    // alumno es un pool fungible (de ahí saldoDisponible como total, no por nota).
    //
    // Corrección de auditoría 2026-09-07: la versión anterior traía primero la lista completa de
    // CreditNote SIN lock (para un pre-chequeo rápido de saldo total) y recién después pedía cada
    // una lockeada por id — pero como la entidad ya quedaba en el persistence context de ESTA
    // transacción desde la primera lectura, Hibernate devolvía esa misma instancia cacheada al
    // "relockearla" en vez de refrescar su estado tras esperar el lock (el SELECT ... FOR UPDATE
    // sí bloquea la fila en la base, pero no pisa los campos ya cargados en memoria). Un
    // consumidor concurrente legítimo terminaba viendo el saldo PRE-commit del otro y fallaba con
    // ObjectOptimisticLockingFailureException al hacer flush, en vez de tener éxito con el saldo
    // real post-commit. Fix: solo se piden ids acá (sin hidratar la entidad completa) — la
    // PRIMERA vez que una CreditNote entra al persistence context de esta transacción es siempre
    // vía findByIdForUpdate, ya bajo lock.
    @Transactional
    public List<CreditApplication> consumeForInvoice(Long studentId, BigDecimal amount, Invoice invoice, Payment payment) {
        List<Long> candidateIds = creditNoteRepository.findIdsByStudentIdAndStatusNotOrderByCreatedAtAsc(studentId, CreditNoteStatus.CONSUMED);

        List<CreditApplication> applications = new ArrayList<>();
        BigDecimal remaining = amount;
        for (Long id : candidateIds) {
            if (remaining.signum() == 0) {
                break;
            }
            CreditNote locked = creditNoteRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new CreditNoteNotFoundException(id));
            BigDecimal noteRemaining = locked.getRemainingAmount();
            if (noteRemaining.signum() == 0) {
                continue;
            }
            BigDecimal toApply = noteRemaining.min(remaining);
            applications.add(locked.consume(toApply, invoice, payment));
            creditNoteRepository.save(locked);
            remaining = remaining.subtract(toApply);
        }
        if (remaining.signum() > 0) {
            throw new InsufficientCreditBalanceException(studentId, amount, amount.subtract(remaining));
        }
        return applications;
    }
}

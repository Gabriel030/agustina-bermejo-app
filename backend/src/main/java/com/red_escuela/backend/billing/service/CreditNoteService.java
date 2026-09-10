package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CreditApplication;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.CreditNoteStatus;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.exception.CreditNoteNotFoundException;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Colaborador interno del pool de crédito de un alumno — las operaciones que un actor pide
// directo (listar, alta manual) viven en usecase/creditnote/, esta clase es lo que varios de
// esos use cases (y RegisterPaymentService, ScholarshipService, EnrollmentWithdrawalCreditService)
// comparten para crear/consumir CreditNote sin duplicar la lógica de saldo/lock.
@Service
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;

    public CreditNoteService(CreditNoteRepository creditNoteRepository) {
        this.creditNoteRepository = creditNoteRepository;
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
    // "relockearla" en vez de refrescar su estado tras esperar el lock. Fix: solo se piden ids
    // acá (sin hidratar la entidad completa) — la PRIMERA vez que una CreditNote entra al
    // persistence context de esta transacción es siempre vía findByIdForUpdate, ya bajo lock.
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

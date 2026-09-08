package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.service.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// Domain Service: coordina Invoice/CreditNote/Student ante un único evento de negocio (baja de
// un enrollment), ninguna de esas entidades puede resolver esto por sí sola. Solo actúa sobre
// bajas VOLUNTARIAS (Fase 6.6, spec "flujo 11") — una baja automática por mora ya llega acá con
// billingStanding=WITHDRAWN_FOR_NONPAYMENT seteado por EnrollmentBillingComplianceSweepService
// ANTES de invocar darDeBaja, así que esa distinción no necesita tocar EnrollmentStatus.
@Service
public class EnrollmentWithdrawalCreditService {

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteService creditNoteService;
    private final CurrentPersonService currentPersonService;

    public EnrollmentWithdrawalCreditService(InvoiceRepository invoiceRepository, CreditNoteService creditNoteService,
                                              CurrentPersonService currentPersonService) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteService = creditNoteService;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public void handleCourseEnrollmentWithdrawal(CourseEnrollment enrollment) {
        if (isNonPaymentWithdrawal(enrollment)) {
            return;
        }
        List<Invoice> pending = invoiceRepository.findByCourseEnrollmentIdAndStatusIn(enrollment.getId(), List.of(InvoiceStatus.PENDING));
        List<Invoice> paid = invoiceRepository.findByCourseEnrollmentIdAndStatusIn(enrollment.getId(), List.of(InvoiceStatus.PAID));
        process(enrollment.getStudent(), pending, paid);
    }

    @Transactional
    public void handleProgramEnrollmentWithdrawal(ProgramEnrollment enrollment) {
        if (isNonPaymentWithdrawal(enrollment)) {
            return;
        }
        List<Invoice> pending = invoiceRepository.findByProgramEnrollmentIdAndStatusIn(enrollment.getId(), List.of(InvoiceStatus.PENDING));
        List<Invoice> paid = invoiceRepository.findByProgramEnrollmentIdAndStatusIn(enrollment.getId(), List.of(InvoiceStatus.PAID));
        process(enrollment.getStudent(), pending, paid);
    }

    private boolean isNonPaymentWithdrawal(BillableEnrollment enrollment) {
        return enrollment.getBillingStanding() == EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT;
    }

    // La Person autenticada solo se resuelve si efectivamente hay algo que cancelar/acreditar —
    // la mayoría de los enrollment sin ninguna Invoice (fixtures de Épica 2 sin facturación, o un
    // enrollment recién creado) no deberían exigir un JWT solo para pasar por acá.
    private void process(Student student, List<Invoice> pending, List<Invoice> paid) {
        YearMonth withdrawalPeriod = YearMonth.now();
        List<Invoice> paidFuturePeriods = paid.stream()
                .filter(invoice -> invoice.getPeriod().isAfter(withdrawalPeriod))
                .toList();
        if (pending.isEmpty() && paidFuturePeriods.isEmpty()) {
            return;
        }

        Person actor = currentPersonService.resolveCurrentPerson();
        String reason = "Baja voluntaria del enrollment";
        for (Invoice invoice : pending) {
            invoice.cancel(actor, reason);
            invoiceRepository.save(invoice);
        }

        if (paidFuturePeriods.isEmpty()) {
            return;
        }
        BigDecimal total = paidFuturePeriods.stream().map(Invoice::getAmountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        CreditNote creditNote = creditNoteService.createFor(student, total, CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED,
                "Baja anticipada — cuotas futuras ya pagas", actor);
        creditNote.getSourceInvoices().addAll(paidFuturePeriods);
    }
}

package com.red_escuela.backend.billing.service;
import com.red_escuela.backend.identity.entity.Student;

import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Money;
import java.time.YearMonth;
import java.util.List;

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
        Money total = paidFuturePeriods.stream().map(Invoice::getAmountDue).reduce(Money::plus).orElseThrow();
        CreditNote creditNote = creditNoteService.createFor(student, total, CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED,
                "Baja anticipada — cuotas futuras ya pagas", actor);
        creditNote.getSourceInvoices().addAll(paidFuturePeriods);
    }
}

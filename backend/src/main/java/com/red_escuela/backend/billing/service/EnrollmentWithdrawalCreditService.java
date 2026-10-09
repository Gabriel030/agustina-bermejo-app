package com.red_escuela.backend.billing.service;
import java.time.Clock;
import com.red_escuela.backend.identity.entity.Student;

import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
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
    private final Clock clock;

    public EnrollmentWithdrawalCreditService(InvoiceRepository invoiceRepository, CreditNoteService creditNoteService,
                                              CurrentPersonService currentPersonService,
                                              Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteService = creditNoteService;
        this.currentPersonService = currentPersonService;
        this.clock = clock;
    }

    @Transactional
    public void handleCourseEnrollmentWithdrawal(CourseEnrollment enrollment) {
        if (isNonPaymentWithdrawal(enrollment)) {
            return;
        }
        List<Invoice> paid = invoiceRepository.findByCourseEnrollmentIdAndPaymentStatusIn(enrollment.getId(), List.of(InvoicePaymentStatus.PAID));
        process(enrollment.getStudent(), paid, "Baja voluntaria del enrollment", "Baja anticipada — cuotas futuras ya pagas");
    }

    @Transactional
    public void handleProgramEnrollmentWithdrawal(ProgramEnrollment enrollment) {
        if (isNonPaymentWithdrawal(enrollment)) {
            return;
        }
        List<Invoice> paid = invoiceRepository.findByProgramEnrollmentIdAndPaymentStatusIn(enrollment.getId(), List.of(InvoicePaymentStatus.PAID));
        process(enrollment.getStudent(), paid, "Baja voluntaria del enrollment", "Baja anticipada — cuotas futuras ya pagas");
    }

    @Transactional
    public void handleProgramEnrollmentGraduation(ProgramEnrollment enrollment) {
        List<Invoice> paid = invoiceRepository.findByProgramEnrollmentIdAndPaymentStatusIn(enrollment.getId(), List.of(InvoicePaymentStatus.PAID));
        process(enrollment.getStudent(), paid, "Egreso de la carrera", "Egreso anticipado — cuotas futuras ya pagas");
    }

    private boolean isNonPaymentWithdrawal(BillableEnrollment enrollment) {
        return enrollment.getPaymentStatus() == EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT;
    }

    private void process(Student student, List<Invoice> paid, String reason, String creditNoteDescription) {
        YearMonth withdrawalPeriod = YearMonth.now(clock);
        List<Invoice> paidFuturePeriods = paid.stream()
                .filter(invoice -> invoice.getPeriod().isAfter(withdrawalPeriod))
                .toList();
        if (paidFuturePeriods.isEmpty()) {
            return;
        }

        Person actor = currentPersonService.resolveCurrentPerson();

        if (paidFuturePeriods.isEmpty()) {
            return;
        }
        Money total = paidFuturePeriods.stream().map(invoice -> invoice.getAmounts().total().value()).reduce(Money::plus).orElseThrow();
        CreditNote creditNote = creditNoteService.createFor(student, total, CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED,
                creditNoteDescription, actor);
        creditNote.getSourceInvoices().addAll(paidFuturePeriods);
    }
}

package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.YearCycle;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.exception.ProgramEnrollmentMissingCohortException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class ProgramEnrollmentBillingScheduler {

    private final InvoiceFactory invoiceFactory;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final Clock clock;

    public ProgramEnrollmentBillingScheduler(InvoiceFactory invoiceFactory, InvoiceRepository invoiceRepository,
                                              ProgramEnrollmentRepository programEnrollmentRepository, Clock clock) {
        this.invoiceFactory = invoiceFactory;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.clock = clock;
    }

    @Transactional
    public void generateEnrollmentFee(ProgramEnrollment enrollment) {
        generateEnrollmentFee(enrollment, null);
    }

    @Transactional
    public void generateEnrollmentFee(ProgramEnrollment enrollment, PaymentPlanSelection selection) {
        ProgramCohort cohort = enrollment.getProgramCohort();
        if (cohort == null) {
            throw new ProgramEnrollmentMissingCohortException(enrollment.getId());
        }
        ProgramYearBillingTemplate template = cohort.getTemplate();
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        int slot = YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth);
        assignCurrentTemplateIfMissing(enrollment, template);
        boolean alreadyIssued = invoiceRepository.existsByProgramEnrollmentIdAndFeeTypeAndTemplateIdAndEnrollmentFeeSlot(
                enrollment.getId(), FeeType.ENROLLMENT_FEE, template.getId(), slot);
        if (alreadyIssued) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate dueDate = cohort.getCursadaStart().isBefore(today) ? today.plusDays(template.getLateEnrollmentFeeDueDays()) : cohort.getCursadaStart();
        Invoice invoice = invoiceFactory.createForProgramAtomic(enrollment, selection, FeeType.ENROLLMENT_FEE, entryMonth, dueDate, today,
                template.getId(), slot);
        Percentage discount = cohort.getEntryFeeDiscount();
        if (discount != null && discount.fraction().signum() > 0) {
            invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, new MonetaryAdjustment.Proportional(discount),
                    "Matrícula de ingreso con descuento de " + discount.points().stripTrailingZeros().toPlainString() + "%", null);
            invoiceRepository.save(invoice);
        }
    }

    @Transactional
    public void adoptAdmissionFee(ProgramEnrollment enrollment, Invoice admissionInvoice) {
        admissionInvoice.setProgramEnrollment(enrollment);
        ProgramCohort cohort = enrollment.getProgramCohort();
        if (cohort == null) {
            return;
        }
        ProgramYearBillingTemplate template = cohort.getTemplate();
        YearMonth entryMonth = YearMonth.from(cohort.getCursadaStart());
        admissionInvoice.setTemplateId(template.getId());
        admissionInvoice.setEnrollmentFeeSlot(YearCycle.of(template, entryMonth.getYear()).slotOf(entryMonth));
        assignCurrentTemplateIfMissing(enrollment, template);
    }

    private void assignCurrentTemplateIfMissing(ProgramEnrollment enrollment, ProgramYearBillingTemplate template) {
        if (enrollment.getCurrentBillingTemplate() == null) {
            enrollment.setCurrentBillingTemplate(template);
            programEnrollmentRepository.save(enrollment);
        }
    }
}

package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.exception.ProgramEnrollmentMissingCohortException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;

@Service
public class ProgramEnrollmentBillingScheduler {
    private final InvoiceFactory invoiceFactory;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public ProgramEnrollmentBillingScheduler(InvoiceFactory invoiceFactory, InvoiceRepository invoiceRepository,
                                              ProgramEnrollmentRepository programEnrollmentRepository) {
        this.invoiceFactory = invoiceFactory;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
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
        YearMonth period = YearMonth.from(cohort.getCursadaStart());
        Invoice invoice = invoiceFactory.createForProgramAtomic(enrollment, selection, FeeType.ENROLLMENT_FEE, period, cohort.getCursadaStart());
        Percentage discount = cohort.getEntryFeeDiscount();
        if (discount != null && discount.fraction().signum() > 0) {
            invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, new MonetaryAdjustment.Proportional(discount),
                    "Matrícula de ingreso con descuento de " + discount.points().stripTrailingZeros().toPlainString() + "%", null);
            invoiceRepository.save(invoice);
        }
        if (enrollment.getCurrentBillingTemplate() == null) {
            enrollment.setCurrentBillingTemplate(cohort.getTemplate());
            programEnrollmentRepository.save(enrollment);
        }
    }
}

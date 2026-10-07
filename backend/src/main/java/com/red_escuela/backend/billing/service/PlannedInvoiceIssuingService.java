package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingResult;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlannedInvoiceIssuingService {
    private static final Logger log = LoggerFactory.getLogger(PlannedInvoiceIssuingService.class);
    private final InvoiceIssuingService invoiceIssuingService;

    public PlannedInvoiceIssuingService(InvoiceIssuingService invoiceIssuingService) {
        this.invoiceIssuingService = invoiceIssuingService;
    }

    public int issuePlannedProgramInvoices(ProgramEnrollment enrollment, PaymentPlanSelection selection, MonthlyInvoicePlan plan, InvoiceIssuingReason reason) {
        return issuePlannedProgramInvoices(enrollment, selection, plan, reason, false);
    }

    public int issuePlannedProgramInvoices(ProgramEnrollment enrollment, PaymentPlanSelection selection, MonthlyInvoicePlan plan, InvoiceIssuingReason reason, boolean inCurrentTransaction) {
        log.info("Plan de cargos: inscripcion={}, mes={}, cargos={}, motivo={}, decisiones={}",
                plan.enrollmentId(), plan.month(), plan.charges().size(), reason, plan.describe());
        int issued = 0;
        for (PlannedInvoice charge : plan.charges()) {
            if (issuePlannedProgramInvoice(enrollment, selection, charge, reason, inCurrentTransaction) instanceof InvoiceIssuingResult.InvoiceIssued) {
                issued++;
            }
        }
        return issued;
    }

    public InvoiceIssuingResult issuePlannedProgramInvoice(ProgramEnrollment enrollment, PaymentPlanSelection selection, PlannedInvoice charge, InvoiceIssuingReason reason, boolean inCurrentTransaction) {
        FeeType feeType = charge.kind();
        InvoiceIssuingReason effectiveReason = charge.upfront() ? InvoiceIssuingReason.UPFRONT : reason;
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(new InvoiceRecipient.ProgramEnrollmentRecipient(enrollment), feeType, charge.period(), charge.dueDate(), effectiveReason,
                selection, charge.templateId(), charge.slot(), charge.entry(), null, null, null, null);
        InvoiceIssuingResult result = inCurrentTransaction ? invoiceIssuingService.issueInvoiceInCallerTransaction(request) : invoiceIssuingService.issueInvoiceInNewTransaction(request);
        if (result instanceof InvoiceIssuingResult.InvoiceIssuingRejected rejected) {
            throw new ChargeRejectedException(rejected.evaluation());
        }
        return result;
    }

    public int issuePlannedCourseInvoices(CourseEnrollment enrollment, PaymentPlanSelection selection, MonthlyInvoicePlan plan) {
        log.info("Plan de cargos de curso: inscripcion={}, mes={}, cargos={}, decisiones={}", plan.enrollmentId(), plan.month(), plan.charges().size(), plan.describe());
        int issued = 0;
        for (PlannedInvoice charge : plan.charges()) {
            InvoiceIssuingReason reason = charge.upfront() ? InvoiceIssuingReason.UPFRONT : InvoiceIssuingReason.COURSE_MONTHLY_RUN;
            InvoiceIssuingRequest request = new InvoiceIssuingRequest(new InvoiceRecipient.CourseEnrollmentRecipient(enrollment), FeeType.MONTHLY_INSTALLMENT, charge.period(), charge.dueDate(),
                    reason, selection, null, null, false, null, null, null, null);
            InvoiceIssuingResult result = invoiceIssuingService.issueInvoiceInNewTransaction(request);
            if (result instanceof InvoiceIssuingResult.InvoiceIssuingRejected rejected) {
                throw new ChargeRejectedException(rejected.evaluation());
            }
            if (result instanceof InvoiceIssuingResult.InvoiceIssued) {
                issued++;
            }
        }
        return issued;
    }
}

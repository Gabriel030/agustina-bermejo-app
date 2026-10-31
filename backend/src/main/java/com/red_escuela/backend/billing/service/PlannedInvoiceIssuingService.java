package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
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
        return issuePlannedProgramInvoice(enrollment, selection, charge, reason, inCurrentTransaction, InvoiceIssuanceDestination.EMIT_NOW);
    }

    public InvoiceIssuingResult issuePlannedProgramInvoice(ProgramEnrollment enrollment, PaymentPlanSelection selection, PlannedInvoice charge, InvoiceIssuingReason reason,
                                                           boolean inCurrentTransaction, InvoiceIssuanceDestination destination) {
        if (inCurrentTransaction && !InvoiceIssuanceDestination.EMIT_NOW.equals(destination)) {
            throw new IllegalArgumentException("Issuing in the caller transaction only supports immediate emission, got " + destination);
        }
        var request = InvoiceIssuingRequestFactory.createProgramEnrollmentRequest(enrollment, selection, charge, reason);
        InvoiceIssuingResult result = inCurrentTransaction ? invoiceIssuingService.issueInvoiceInCallerTransaction(request) : invoiceIssuingService.issueInvoiceInNewTransaction(request, destination);
        if (result instanceof InvoiceIssuingResult.InvoiceIssuingRejected rejected) {
            throw new ChargeRejectedException(rejected.evaluationResult());
        }
        return result;
    }

    public int issuePlannedCourseInvoices(CourseEnrollment enrollment, PaymentPlanSelection selection, MonthlyInvoicePlan plan) {
        log.info("Plan de cargos de curso: inscripcion={}, mes={}, cargos={}, decisiones={}", plan.enrollmentId(), plan.month(), plan.charges().size(), plan.describe());
        int issued = 0;
        for (PlannedInvoice charge : plan.charges()) {
            if (issuePlannedCourseInvoice(enrollment, selection, charge) instanceof InvoiceIssuingResult.InvoiceIssued) {
                issued++;
            }
        }
        return issued;
    }

    public InvoiceIssuingResult issuePlannedCourseInvoice(CourseEnrollment enrollment, PaymentPlanSelection selection, PlannedInvoice charge) {
        return issuePlannedCourseInvoice(enrollment, selection, charge, InvoiceIssuanceDestination.EMIT_NOW);
    }

    public InvoiceIssuingResult issuePlannedCourseInvoice(CourseEnrollment enrollment, PaymentPlanSelection selection, PlannedInvoice charge, InvoiceIssuanceDestination destination) {
        var request = InvoiceIssuingRequestFactory.createCourseEnrollmentRequest(enrollment, selection, charge, InvoiceIssuingReason.COURSE_MONTHLY_RUN);
        InvoiceIssuingResult result = invoiceIssuingService.issueInvoiceInNewTransaction(request, destination);
        if (result instanceof InvoiceIssuingResult.InvoiceIssuingRejected rejected) {
            throw new ChargeRejectedException(rejected.evaluationResult());
        }
        return result;
    }
}

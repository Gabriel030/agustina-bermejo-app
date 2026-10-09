package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.exception.DuplicateChargeException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class ProgramAdmissionInvoiceIssuer {
    private final InvoiceIssuingService invoiceIssuingService;
    private final Clock clock;

    public ProgramAdmissionInvoiceIssuer(InvoiceIssuingService invoiceIssuingService, Clock clock) {
        this.invoiceIssuingService = invoiceIssuingService;
        this.clock = clock;
    }

    @Transactional
    public Invoice issueAdmissionInvoice(Long studentId, Program program, ProgramCohort cohort) {
        return switch (invoiceIssuingService.issueInvoiceInCallerTransaction(
                InvoiceIssuingRequestFactory.createProgramAdmissionRequest(studentId, program, cohort, LocalDate.now(clock)))) {
            case InvoiceIssuingResult.InvoiceIssued issued -> issued.invoice();
            case InvoiceIssuingResult.InvoiceAlreadyIssued existing -> throw new DuplicateChargeException("admission");
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
        };
    }
}

package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.ProgramAdmissionInvoiceIssuer;
import com.red_escuela.backend.enrollment.api.ProgramAdmissionInvoicePort;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BillingProgramAdmissionInvoiceAdapter implements ProgramAdmissionInvoicePort {

    private final ProgramAdmissionInvoiceIssuer issuer;
    private final CurrentBillingModePort billingMode;

    public BillingProgramAdmissionInvoiceAdapter(ProgramAdmissionInvoiceIssuer issuer, CurrentBillingModePort billingMode) {
        this.issuer = issuer;
        this.billingMode = billingMode;
    }

    @Override
    public Optional<Invoice> issueAdmissionInvoice(Long studentId, Program program, ProgramCohort cohort) {
        if (billingMode.currentMode() == BillingMode.OFF) {
            return Optional.empty();
        }
        return Optional.of(issuer.issueAdmissionInvoice(studentId, program, cohort));
    }
}

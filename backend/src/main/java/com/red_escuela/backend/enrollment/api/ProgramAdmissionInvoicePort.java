package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import java.util.Optional;

public interface ProgramAdmissionInvoicePort {

    Optional<Invoice> issueAdmissionInvoice(Long studentId, Program program, ProgramCohort cohort);
}

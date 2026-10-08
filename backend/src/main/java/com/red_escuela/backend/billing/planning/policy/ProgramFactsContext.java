package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.LectiveYearBillingTerms;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;

public interface ProgramFactsContext {
    ProgramInvoicePlanningFacts facts();

    LectiveYearBillingTerms terms();

    BillingConsultation consultation();

    String notIssuedDetail(String cause);
}

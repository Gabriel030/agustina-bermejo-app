package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;

public interface InvoicePlanningPolicy<C> extends Policy<C> {
    int plannerQuestionNumber();
}

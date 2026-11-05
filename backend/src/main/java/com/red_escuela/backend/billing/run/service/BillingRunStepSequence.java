package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.operation.AbstractBillingRunStep;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class BillingRunStepSequence {
    private final List<AbstractBillingRunStep> orderedSteps;

    public BillingRunStepSequence(List<AbstractBillingRunStep> steps) {
        this.orderedSteps = steps.stream().sorted(Comparator.comparing(AbstractBillingRunStep::step)).toList();
    }

    public List<AbstractBillingRunStep> orderedSteps() {
        return orderedSteps;
    }
}

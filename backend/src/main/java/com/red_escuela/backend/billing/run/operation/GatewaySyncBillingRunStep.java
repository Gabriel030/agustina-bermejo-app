package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class GatewaySyncBillingRunStep extends AbstractBillingRunStep {
    public static final String GATEWAY_DISABLED = "GATEWAY_DISABLED";

    @Override
    public BillingRunStep step() {
        return BillingRunStep.GATEWAY_SYNC;
    }

    @Override
    public Optional<String> findNotApplicableCode() {
        return Optional.of(GATEWAY_DISABLED);
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        return new BillingRunStepOutcome(0, 0, 0, 0, List.of());
    }
}

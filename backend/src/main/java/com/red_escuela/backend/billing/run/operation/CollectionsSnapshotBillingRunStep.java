package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CollectionsSnapshotBillingRunStep extends AbstractBillingRunStep {
    private final CollectionsDailySnapshotStep snapshotStep;

    public CollectionsSnapshotBillingRunStep(CollectionsDailySnapshotStep snapshotStep) {
        this.snapshotStep = snapshotStep;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.COLLECTIONS_SNAPSHOT;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        snapshotStep.takeCollectionsSnapshot(businessDate);
        return new BillingRunStepOutcome(1, 1, 0, 0, List.of());
    }
}

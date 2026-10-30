package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.operation.CollectionReminderPlanningResult;
import com.red_escuela.backend.billing.collections.operation.CollectionReminderPlanningStep;
import com.red_escuela.backend.billing.collections.operation.MissedReminderDaysFinder;
import com.red_escuela.backend.billing.collections.operation.OverdueContactListPublicationStep;
import com.red_escuela.backend.billing.collections.operation.PostWithdrawalSummaryPlanningStep;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRunStore;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CollectionReminderPlanningBillingRunStep extends AbstractBillingRunStep {
    private static final Logger log = LoggerFactory.getLogger(CollectionReminderPlanningBillingRunStep.class);

    private final CollectionReminderPlanningStep planningStep;
    private final OverdueContactListPublicationStep contactListStep;
    private final PostWithdrawalSummaryPlanningStep postWithdrawalSummaryStep;
    private final MissedReminderDaysFinder missedDaysFinder;
    private final CollectionReminderRunStore runStore;

    public CollectionReminderPlanningBillingRunStep(CollectionReminderPlanningStep planningStep, OverdueContactListPublicationStep contactListStep,
                                                    PostWithdrawalSummaryPlanningStep postWithdrawalSummaryStep, MissedReminderDaysFinder missedDaysFinder,
                                                    CollectionReminderRunStore runStore) {
        this.planningStep = planningStep;
        this.contactListStep = contactListStep;
        this.postWithdrawalSummaryStep = postWithdrawalSummaryStep;
        this.missedDaysFinder = missedDaysFinder;
        this.runStore = runStore;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.COLLECTION_REMINDER_PLANNING;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        MissedReminderRecovery recovery = missedDaysFinder.findRecoveryBefore(businessDate);
        if (recovery.isRecovering()) {
            log.info("Recuperación de recordatorios de cobranza: fecha={}, diasSinCorrida={}", businessDate, recovery.missedDays());
        }
        CollectionReminderPlanningResult result = planningStep.planCollectionReminders(businessDate, recovery);
        if (result.failed() == 0) {
            runStore.recordCoveredDay(businessDate);
            recovery.missedDays().forEach(runStore::recordCoveredDay);
        }
        int sideTaskFailures = publishContactList(businessDate) + planPostWithdrawalSummaries(businessDate);
        List<BillingRunItemRecord> failedStudents = result.errors().stream()
                .map(error -> BillingRunItemRecord.failedSubject(BillingRunSubjectType.STUDENT, error.studentId(), SUBJECT_PROCESSING_FAILED, error.message())).toList();
        return new BillingRunStepOutcome(result.candidates(), result.planned() + result.nothingToSend(), result.alreadyPlanned(), result.failed() + sideTaskFailures, failedStudents);
    }

    private int publishContactList(LocalDate businessDate) {
        try {
            contactListStep.publishContactListOf(businessDate);
            return 0;
        } catch (RuntimeException failure) {
            log.warn("Falló la publicación de la lista para contactar: fecha={}", businessDate, failure);
            return 1;
        }
    }

    private int planPostWithdrawalSummaries(LocalDate businessDate) {
        try {
            postWithdrawalSummaryStep.planPostWithdrawalSummariesOf(businessDate);
            return 0;
        } catch (RuntimeException failure) {
            log.warn("Falló la planificación de los resúmenes post-baja: fecha={}", businessDate, failure);
            return 1;
        }
    }
}

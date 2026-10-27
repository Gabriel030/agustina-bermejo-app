package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.CohortPromotionStatus;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionCatalogEntryAssembler;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-432")
@Tag("D-304")
class CohortPromotionDerivedStatusIT extends AbstractCohortPromotionAcquisitionIT {
    private static final LocalDate DURING_THE_WINDOW = LocalDate.of(2027, 2, 1);

    @Autowired
    private DiscountDefinitionCatalogEntryAssembler assembler;

    private CohortPromotionStatus statusOf(Long definitionId, LocalDate today) {
        MutableClock.INSTANCE.setDate(today);
        return transactionTemplate.execute(status -> assembler.assembleCatalogEntry(definitionRepository.findById(definitionId).orElseThrow()).cohortPromotionStatus().orElseThrow());
    }

    @Test
    void thePromotionFillsUpOnItsOwnAcquisitionsAndAnotherPromotionWithRoomStaysOpen() {
        Long quotaOfTwo = publish(promotion("Cupo de dos", "10", 2));
        Long quotaOfFive = publish(promotion("Cupo de cinco", "5", 5));
        IssuingScenarioFactory.Learner first = newLearner();
        IssuingScenarioFactory.Learner second = newLearner();
        assertThat(statusOf(quotaOfTwo, DURING_THE_WINDOW)).isEqualTo(CohortPromotionStatus.OPEN);

        acquire(first);
        assertThat(placesUsed(quotaOfTwo)).isEqualTo(1);
        assertThat(statusOf(quotaOfTwo, DURING_THE_WINDOW)).isEqualTo(CohortPromotionStatus.OPEN);
        acquire(second);

        assertThat(placesUsed(quotaOfTwo)).isEqualTo(2);
        assertThat(statusOf(quotaOfTwo, DURING_THE_WINDOW)).isEqualTo(CohortPromotionStatus.QUOTA_FULL);
        assertThat(placesUsed(quotaOfFive)).isZero();
        assertThat(statusOf(quotaOfFive, DURING_THE_WINDOW)).isEqualTo(CohortPromotionStatus.OPEN);
    }

    @Test
    void everyAcquisitionCountsTowardsTheQuotaWhateverItsStatus() {
        Long definitionId = publish(promotion("Cupo de uno", "10", 1));
        IssuingScenarioFactory.Learner learner = newLearner();
        acquire(learner);
        jdbc.update("update student_discount set status = 'EXHAUSTED' where discount_definition_id = ?", definitionId);

        assertThat(statusOf(definitionId, DURING_THE_WINDOW)).isEqualTo(CohortPromotionStatus.QUOTA_FULL);
    }
}

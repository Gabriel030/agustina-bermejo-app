package com.red_escuela.backend.evaluation.entity;

import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionClosureStudentSettlementTest {

    private static final Grade GRADE = Grade.of(new BigDecimal("8.00"));

    private CommissionClosureStudent conditionalRow(Boolean administrativeOk, Boolean documentsComplete, Boolean correlativesOk) {
        return CommissionClosureStudent.builder()
                .finalCondition(ClosureFinalCondition.PROMOTION_CONDITIONAL)
                .promotionGrade(GRADE)
                .administrativeOk(administrativeOk)
                .documentsComplete(documentsComplete)
                .correlativesOk(correlativesOk)
                .build();
    }

    @Test
    void givenOnlyRequirementsPending_whenSettling_thenStaysConditionalKeepingTheGrade() {
        CommissionClosureStudent row = conditionalRow(true, true, false);

        row.settleConditionalPromotion(false);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.PROMOTION_CONDITIONAL);
        assertThat(row.getPromotionGrade()).isEqualTo(GRADE);
    }

    @Test
    void givenAdministrativeBlockWithoutExceptionAndRequirementsPending_whenSettling_thenGoesToFinal() {
        CommissionClosureStudent row = conditionalRow(false, true, false);

        row.settleConditionalPromotion(false);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.GOES_TO_FINAL);
        assertThat(row.getPromotionGrade()).isNull();
    }

    @Test
    void givenIncompleteDocumentsWithoutExceptionAndRequirementsPending_whenSettling_thenGoesToFinal() {
        CommissionClosureStudent row = conditionalRow(true, false, false);

        row.settleConditionalPromotion(false);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.GOES_TO_FINAL);
    }

    @Test
    void givenAdministrativeBlockWithExceptionAndRequirementsPending_whenSettling_thenStaysConditional() {
        CommissionClosureStudent row = conditionalRow(false, false, false);

        row.settleConditionalPromotion(true);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.PROMOTION_CONDITIONAL);
        assertThat(row.getPromotionGrade()).isEqualTo(GRADE);
    }

    @Test
    void givenAdministrativeBlockWithExceptionAndRequirementsMet_whenSettling_thenPromotes() {
        CommissionClosureStudent row = conditionalRow(false, true, true);

        row.settleConditionalPromotion(true);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.PROMOTES);
    }

    @Test
    void givenAdministrativeBlockWithoutExceptionAndRequirementsMet_whenSettling_thenGoesToFinal() {
        CommissionClosureStudent row = conditionalRow(false, true, true);

        row.settleConditionalPromotion(false);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.GOES_TO_FINAL);
        assertThat(row.getPromotionGrade()).isNull();
    }

    @Test
    void givenNonConditionalRow_whenSettling_thenItIsUntouched() {
        CommissionClosureStudent row = CommissionClosureStudent.builder()
                .finalCondition(ClosureFinalCondition.PROMOTES).promotionGrade(GRADE).correlativesOk(false).build();

        row.settleConditionalPromotion(false);

        assertThat(row.getFinalCondition()).isEqualTo(ClosureFinalCondition.PROMOTES);
    }
}

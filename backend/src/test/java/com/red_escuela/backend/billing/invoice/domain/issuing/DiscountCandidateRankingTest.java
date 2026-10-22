package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.studentDiscountCandidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.withOrigin;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-295")
@Tag("D-384")
@Tag("D-385")
class DiscountCandidateRankingTest {

    private static final Instant EARLY = Instant.parse("2027-02-01T10:00:00Z");
    private static final Instant LATE = Instant.parse("2027-02-02T10:00:00Z");

    private final DiscountCandidateRanking ranking = new DiscountCandidateRanking();

    private List<InvoiceDiscountCandidate> rankOf(InvoiceDiscountCandidate... candidates) {
        return List.of(candidates).stream().sorted(ranking).toList();
    }

    @Test
    void aLargerSavingWinsOverAPercentageWithLessSaving() {
        var scholarshipWithLessSaving = candidate(DiscountType.SCHOLARSHIP, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "30.00");
        var annualWithMoreSaving = candidate(DiscountType.ANNUAL_UPFRONT, 2, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "31.00");

        assertThat(rankOf(scholarshipWithLessSaving, annualWithMoreSaving)).containsExactly(annualWithMoreSaving, scholarshipWithLessSaving);
        assertThat(ranking.ranksAbove(annualWithMoreSaving, scholarshipWithLessSaving)).isTrue();
        assertThat(ranking.ranksAbove(scholarshipWithLessSaving, annualWithMoreSaving)).isFalse();
    }

    @Test
    void anEqualSavingIsBrokenByTypeWithTheScholarshipFirst() {
        var annual = studentDiscountCandidate(DiscountType.ANNUAL_UPFRONT, 1, 1, "40.00", 1, EARLY);
        var promotion = studentDiscountCandidate(DiscountType.COHORT_PROMOTION, 2, 2, "40.00", 2, EARLY);
        var scholarship = studentDiscountCandidate(DiscountType.SCHOLARSHIP, 3, 3, "40.00", 3, LATE);

        assertThat(rankOf(annual, promotion, scholarship)).containsExactly(scholarship, promotion, annual);
        assertThat(rankOf(scholarship, promotion, annual)).containsExactly(scholarship, promotion, annual);
    }

    @Test
    void individualAndCohortEntryRankLikeACohortPromotion() {
        var scholarship = studentDiscountCandidate(DiscountType.SCHOLARSHIP, 9, 9, "40.00", 9, LATE);
        var annual = studentDiscountCandidate(DiscountType.ANNUAL_UPFRONT, 1, 1, "40.00", 1, EARLY);
        var promotion = studentDiscountCandidate(DiscountType.COHORT_PROMOTION, 2, 2, "40.00", 3, EARLY);
        var individual = studentDiscountCandidate(DiscountType.INDIVIDUAL, 3, 3, "40.00", 2, EARLY);
        var cohortEntry = studentDiscountCandidate(DiscountType.COHORT_ENTRY, 4, 4, "40.00", 1, EARLY);

        assertThat(promotion.typeRank()).isEqualTo(individual.typeRank()).isEqualTo(cohortEntry.typeRank());
        assertThat(rankOf(annual, promotion, individual, cohortEntry, scholarship)).containsExactly(scholarship, cohortEntry, individual, promotion, annual);
    }

    @Test
    @Tag("D-285")
    void anEqualSavingAndTypeIsBrokenByPriority() {
        var secondPriority = studentDiscountCandidate(DiscountType.COHORT_PROMOTION, 1, 1, "40.00", 2, EARLY);
        var firstPriority = studentDiscountCandidate(DiscountType.COHORT_PROMOTION, 2, 2, "40.00", 1, LATE);

        assertThat(rankOf(secondPriority, firstPriority)).containsExactly(firstPriority, secondPriority);
    }

    @Test
    @Tag("D-285")
    void anEqualPriorityIsBrokenByTheEarliestGrant() {
        var lateGrant = studentDiscountCandidate(DiscountType.SCHOLARSHIP, 1, 1, "40.00", 1, LATE);
        var earlyGrant = studentDiscountCandidate(DiscountType.SCHOLARSHIP, 2, 2, "40.00", 1, EARLY);

        assertThat(rankOf(lateGrant, earlyGrant)).containsExactly(earlyGrant, lateGrant);
    }

    @Test
    void anEqualGrantInstantIsBrokenByTheLowestStudentDiscountId() {
        var highId = studentDiscountCandidate(DiscountType.SCHOLARSHIP, 1, 20, "40.00", 1, EARLY);
        var lowId = studentDiscountCandidate(DiscountType.SCHOLARSHIP, 2, 10, "40.00", 1, EARLY);

        assertThat(rankOf(highId, lowId)).containsExactly(lowId, highId);
    }

    @Test
    void candidatesWithoutPriorityOrGrantRankAfterThoseThatHaveThem() {
        var legacy = candidate(DiscountType.COHORT_PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00");
        var withPriorityOnly = withPriority(legacy, 1, null);
        var withGrantOnly = withPriority(legacy, null, LATE);
        var withBoth = studentDiscountCandidate(DiscountType.COHORT_PROMOTION, 1, 1, "40.00", 1, LATE);

        assertThat(rankOf(legacy, withGrantOnly, withPriorityOnly, withBoth)).containsExactly(withBoth, withPriorityOnly, withGrantOnly, legacy);
        assertThat(rankOf(withPriorityOnly, withBoth)).first().isEqualTo(withBoth);
    }

    @Test
    void anEqualEverythingButTheOriginIsBrokenByTheOriginOrder() {
        var studentDiscount = withOrigin(candidate(DiscountType.COHORT_PROMOTION, 5, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00"), DiscountCandidateOrigin.STUDENT_DISCOUNT);
        var legacyCohortEntry = withOrigin(candidate(DiscountType.COHORT_PROMOTION, 5, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00"), DiscountCandidateOrigin.LEGACY_COHORT_ENTRY);

        assertThat(rankOf(studentDiscount, legacyCohortEntry)).containsExactly(legacyCohortEntry, studentDiscount);
    }

    @Test
    void anEqualEverythingButTheSourceIdIsBrokenByTheLowestSourceId() {
        var high = withoutStudentDiscountId(candidate(DiscountType.COHORT_PROMOTION, 8, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00"));
        var low = withoutStudentDiscountId(candidate(DiscountType.COHORT_PROMOTION, 3, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00"));
        var withStudentDiscountId = candidate(DiscountType.COHORT_PROMOTION, 20, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00");

        assertThat(rankOf(high, low)).containsExactly(low, high);
        assertThat(rankOf(high, withStudentDiscountId)).containsExactly(withStudentDiscountId, high);
    }

    @Test
    void aCandidateDoesNotRankAboveItself() {
        var only = candidate(DiscountType.COHORT_PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "40.00");

        assertThat(ranking.ranksAbove(only, only)).isFalse();
        assertThat(ranking.compare(only, only)).isZero();
    }

    private static InvoiceDiscountCandidate withoutStudentDiscountId(InvoiceDiscountCandidate base) {
        return new InvoiceDiscountCandidate(base.discountType(), base.sourceId(), null, base.discountName(), base.applicableInvoiceType(),
                base.baseAmount(), base.discountAmount(), base.installmentNumber(), base.installmentsTotal(), base.lineNumber(),
                base.priority(), base.grantedAt(), base.calculation(), base.origin());
    }

    private static InvoiceDiscountCandidate withPriority(InvoiceDiscountCandidate base, Integer priority, Instant grantedAt) {
        return new InvoiceDiscountCandidate(base.discountType(), base.sourceId(), base.studentDiscountId(), base.discountName(), base.applicableInvoiceType(),
                base.baseAmount(), base.discountAmount(), base.installmentNumber(), base.installmentsTotal(), base.lineNumber(),
                priority, grantedAt, base.calculation(), base.origin());
    }
}

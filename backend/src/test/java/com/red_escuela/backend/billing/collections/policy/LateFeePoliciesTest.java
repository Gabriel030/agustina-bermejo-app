package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.PlannedLateFeePeriod;
import com.red_escuela.backend.billing.events.LateFeeCutoffKind;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LateFeePoliciesTest {
    private static final StudentSnapshot RECIPIENT = new StudentSnapshot("Ana Perez", "30111222", null);
    private static final LocalDate FACT_DATE = LocalDate.of(2027, 6, 18);

    private static LatePaymentTerms terms(boolean freeze, boolean enabled) {
        return new LatePaymentTerms(5, Percentage.ofPoints(new BigDecimal("0.20")), 30, 1, true, 10, freeze, enabled);
    }

    private static LateFeeOriginFacts origin(InvoiceDocumentType documentType, InvoiceType type, InvoicePaymentStatus status) {
        return new LateFeeOriginFacts(1L, "F-00000001", 7L, documentType, type, status, LocalDate.of(2027, 3, 12), Currency.ARS,
                NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS), RECIPIENT, YearMonth.of(2027, 3), 3L, null);
    }

    private static final LocalDate PERIOD_START = LocalDate.of(2027, 6, 1);

    private static LateFeeIssuingContext context(LateFeeOriginFacts origin, LateFeeDebitNoteDraftFacts draft, LatePaymentTerms cutoffTerms, Optional<LocalDate> end) {
        return contextWithChargedDays(origin, draft, cutoffTerms, end, 18);
    }

    private static LateFeeIssuingContext contextWithChargedDays(LateFeeOriginFacts origin, LateFeeDebitNoteDraftFacts draft, LatePaymentTerms cutoffTerms, Optional<LocalDate> end,
                                                                int chargedDays) {
        return new LateFeeIssuingContext(origin, draft, new PlannedLateFeePeriod(new LateFeeAccrualPeriod(PERIOD_START, FACT_DATE), chargedDays, LateFeeCutoffKind.MONTH_CLOSE), 9L,
                terms(true, true), day -> cutoffTerms.freezeLateFeesOnWithdrawal() && end.map(day::isAfter).orElse(false), end, NonNegativeMoney.of(new BigDecimal("4000.00"), Currency.ARS), LocalDate.of(2027, 6, 18), Optional.empty());
    }

    private static LateFeeIssuingContext standard() {
        LateFeeOriginFacts origin = origin(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoicePaymentStatus.OVERDUE);
        return context(origin, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin), terms(true, true), Optional.empty());
    }

    @Test
    @Tag("D-289")
    @Tag("D-149")
    @Tag("D-155")
    void onlyMonthlyInstallmentsAndEnrollmentFeesAccrueLateFees() {
        Set<InvoiceType> accruing = EnumSet.of(InvoiceType.MONTHLY_INSTALLMENT, InvoiceType.ENROLLMENT_FEE);
        for (InvoiceType type : InvoiceType.values()) {
            LateFeeOriginFacts origin = origin(type == InvoiceType.LATE_FEE ? InvoiceDocumentType.DEBIT_NOTE : InvoiceDocumentType.INVOICE, type, InvoicePaymentStatus.OVERDUE);
            var verdict = new LateFeeAppliesToInvoiceTypePolicy().evaluateRule(context(origin, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin), terms(true, true), Optional.empty()));
            assertThat(verdict.allowed()).as(type.name()).isEqualTo(accruing.contains(type));
            assertThat(verdict.code()).isEqualTo(accruing.contains(type) ? null : "LATE_FEE_NOT_APPLICABLE");
        }
        LateFeeOriginFacts debitNoteOfMonthlyType = origin(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.MONTHLY_INSTALLMENT, InvoicePaymentStatus.OVERDUE);
        assertThat(new LateFeeAppliesToInvoiceTypePolicy().evaluateRule(context(debitNoteOfMonthlyType, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(debitNoteOfMonthlyType),
                terms(true, true), Optional.empty())).allowed()).isFalse();
    }

    @Test
    @Tag("D-153")
    void anOriginUnderPaymentReviewNeverAccrues() {
        for (InvoicePaymentStatus status : InvoicePaymentStatus.values()) {
            LateFeeOriginFacts origin = origin(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, status);
            var verdict = new OriginNotUnderPaymentReviewPolicy().evaluateRule(context(origin, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin), terms(true, true), Optional.empty()));
            assertThat(verdict.allowed()).as(status.name()).isEqualTo(status != InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        }
        LateFeeOriginFacts underReview = origin(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        assertThat(new OriginNotUnderPaymentReviewPolicy().evaluateRule(context(underReview, LateFeeDebitNoteDraftFacts.addressedLikeOrigin(underReview), terms(true, true),
                Optional.empty())).code()).isEqualTo("ORIGIN_UNDER_PAYMENT_REVIEW");
    }

    @Test
    @Tag("D-210")
    @Tag("D-294")
    void chargingNeedsAtLeastOneEnabledDayInThePeriod() {
        LateFeeOriginFacts origin = standard().origin();
        var draft = LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin);
        assertThat(new LateFeeChargingEnabledPolicy().evaluateRule(contextWithChargedDays(origin, draft, terms(true, true), Optional.empty(), 12)).allowed()).isTrue();
        assertThat(new LateFeeChargingEnabledPolicy().evaluateRule(contextWithChargedDays(origin, draft, terms(true, false), Optional.empty(), 1)).allowed()).isTrue();
        var denied = new LateFeeChargingEnabledPolicy().evaluateRule(contextWithChargedDays(origin, draft, terms(true, true), Optional.empty(), 0));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.code()).isEqualTo("LATE_FEE_CHARGING_DISABLED");
    }

    @Test
    @Tag("D-148")
    @Tag("D-293")
    void aLateFeeIsFrozenOnlyWhenThePeriodStartsAfterTheEndAndFreezingIsOn() {
        LateFeeOriginFacts origin = standard().origin();
        var draft = LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin);
        LateFeeNotFrozenPolicy policy = new LateFeeNotFrozenPolicy();
        assertThat(policy.evaluateRule(context(origin, draft, terms(true, true), Optional.empty())).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(origin, draft, terms(true, true), Optional.of(FACT_DATE))).allowed()).isTrue();
        assertThat(policy.evaluateRule(contextWithChargedDays(origin, draft, terms(true, true), Optional.of(PERIOD_START), 1)).allowed()).isTrue();
        var frozen = policy.evaluateRule(context(origin, draft, terms(true, true), Optional.of(PERIOD_START.minusDays(1))));
        assertThat(frozen.allowed()).isFalse();
        assertThat(frozen.code()).isEqualTo("LATE_FEE_FROZEN");
        assertThat(policy.evaluateRule(context(origin, draft, terms(false, true), Optional.of(PERIOD_START.minusDays(1)))).allowed()).isTrue();
        Optional<LocalDate> endedMidPeriod = Optional.of(PERIOD_START.plusDays(8));
        assertThat(policy.evaluateRule(contextWithChargedDays(origin, draft, terms(true, true), endedMidPeriod, 9)).allowed()).isTrue();
        assertThat(policy.evaluateRule(contextWithChargedDays(origin, draft, terms(true, true), endedMidPeriod, 10)).code()).isEqualTo("LATE_FEE_FROZEN");
    }

    @Test
    @Tag("D-244")
    @Tag("D-097")
    void aDebitNoteForAnotherRecipientAccountOrCurrencyIsRejected() {
        LateFeeOriginFacts origin = standard().origin();
        LateFeeDebitNoteDraftFacts same = LateFeeDebitNoteDraftFacts.addressedLikeOrigin(origin);
        DebitNoteSameRecipientAsOriginPolicy policy = new DebitNoteSameRecipientAsOriginPolicy();
        assertThat(policy.evaluateRule(context(origin, same, terms(true, true), Optional.empty())).allowed()).isTrue();
        for (LateFeeDebitNoteDraftFacts other : new LateFeeDebitNoteDraftFacts[]{
                new LateFeeDebitNoteDraftFacts(8L, Currency.ARS, RECIPIENT),
                new LateFeeDebitNoteDraftFacts(7L, Currency.USD, RECIPIENT),
                new LateFeeDebitNoteDraftFacts(7L, Currency.ARS, new StudentSnapshot("Otra Persona", "30111222", null))}) {
            var verdict = policy.evaluateRule(context(origin, other, terms(true, true), Optional.empty()));
            assertThat(verdict.allowed()).isFalse();
            assertThat(verdict.code()).isEqualTo("DEBIT_NOTE_RECIPIENT_MISMATCH");
        }
    }

    @Test
    @Tag("D-239")
    void theIssuingContextComputesTheDebitNoteDueDateFromTheIssueDate() {
        assertThat(standard().debitNoteDueDate()).isEqualTo(LocalDate.of(2027, 6, 28));
    }
}

package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderDigestDomainTest {

    private static final LocalDate DUE = LocalDate.parse("2027-03-11");

    private static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private static ReminderDigestLine line(long id, String amount, boolean underReview) {
        return new ReminderDigestLine(id, null, InvoiceType.MONTHLY_INSTALLMENT, false, DUE, ars(amount), underReview, false, List.of(), ReminderConfigurationVersions.none());
    }

    @Test
    void severityGrowsFromIssuanceToTheSuspensionWarning() {
        List<ReminderMessageType> ascending = List.of(ReminderMessageType.ISSUANCE_NOTICE, ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMessageType.BEFORE_DUE_REMINDER,
                ReminderMessageType.AFTER_DUE_REMINDER, ReminderMessageType.BEFORE_SUSPENSION_REMINDER);

        for (int index = 1; index < ascending.size(); index++) {
            assertThat(ReminderMessageSeverity.rankOf(ascending.get(index))).isGreaterThan(ReminderMessageSeverity.rankOf(ascending.get(index - 1)));
        }
        assertThat(ReminderMessageSeverity.mostSevereOf(ascending)).isEqualTo(ReminderMessageType.BEFORE_SUSPENSION_REMINDER);
        assertThat(ReminderMessageSeverity.mostSevereOf(List.of(ReminderMessageType.AFTER_DUE_REMINDER, ReminderMessageType.BEFORE_DUE_REMINDER)))
                .isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
    }

    @Test
    @Tag("D-139")
    void creditCoveringTheNextInvoiceShowsNoShortfall() {
        ReminderCurrencyBlock covering = ReminderCurrencyBlock.of(Currency.ARS, List.of(line(1L, "100000.00", false)), List.of(line(1L, "100000.00", false)), ars("100000.00"));
        ReminderCurrencyBlock exceeding = ReminderCurrencyBlock.of(Currency.ARS, List.of(line(1L, "100000.00", false)), List.of(line(1L, "100000.00", false)), ars("100000.01"));
        ReminderCurrencyBlock short1 = ReminderCurrencyBlock.of(Currency.ARS, List.of(line(1L, "100000.00", false)), List.of(line(1L, "100000.00", false)), ars("99999.99"));

        assertThat(covering.hasCreditShortfall()).isFalse();
        assertThat(covering.missingForNext().value().isZero()).isTrue();
        assertThat(exceeding.hasCreditShortfall()).isFalse();
        assertThat(short1.hasCreditShortfall()).isTrue();
        assertThat(short1.missingForNext().value().amount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @Tag("D-139")
    void noCreditShowsNoShortfall() {
        ReminderCurrencyBlock block = ReminderCurrencyBlock.of(Currency.ARS, List.of(line(1L, "100000.00", false)), List.of(line(1L, "100000.00", false)), NonNegativeMoney.zero(Currency.ARS));

        assertThat(block.hasCreditShortfall()).isFalse();
        assertThat(block.availableCredit().value().isZero()).isTrue();
    }

    @Test
    @Tag("D-139")
    @Tag("D-346")
    void theShortfallPointsAtTheFirstInvoiceNotUnderReviewAndTheTotalAddsEveryLine() {
        ReminderCurrencyBlock block = ReminderCurrencyBlock.of(Currency.ARS, List.of(line(1L, "50000.00", true), line(2L, "100000.00", false), line(3L, "20000.00", false)), List.of(line(1L, "50000.00", true), line(2L, "100000.00", false), line(3L, "20000.00", false)),
                ars("30000.00"));

        assertThat(block.shortfallInvoiceId()).isEqualTo(2L);
        assertThat(block.missingForNext().value().amount()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(block.total().value().amount()).isEqualByComparingTo(new BigDecimal("170000.00"));
    }

    @Test
    @Tag("D-139")
    void everyLineUnderReviewLeavesNothingToCover() {
        ReminderCurrencyBlock block = ReminderCurrencyBlock.of(Currency.ARS, List.of(line(1L, "50000.00", true)), List.of(line(1L, "50000.00", true)), ars("30000.00"));

        assertThat(block.hasCreditShortfall()).isFalse();
    }

    @Test
    void aNoticeFromAnInvoiceOutsideTheListedDebtIsRejected() {
        ReminderDueNotice stray = new ReminderDueNotice(9L, "x", ReminderMessageType.AFTER_DUE_REMINDER);

        assertThatThrownBy(() -> ReminderDigestAssembler.assembleDigestOf(7L, DUE, List.of(), List.of(stray), List.of(), Map.of(), ReminderDigestMode.DAILY_DIGEST, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("9");
        assertThatThrownBy(() -> ReminderDigestAssembler.assembleDigestOf(7L, DUE, List.of(), List.of(), List.of(stray), Map.of(), ReminderDigestMode.DAILY_DIGEST, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("9");
    }

    @Test
    void aMessageWithoutNoticesIsNotComposed() {
        assertThat(ReminderMessage.compose(List.of(line(1L, "100000.00", false)), List.of(line(1L, "100000.00", false)), Map.of())).isEmpty();
    }
}

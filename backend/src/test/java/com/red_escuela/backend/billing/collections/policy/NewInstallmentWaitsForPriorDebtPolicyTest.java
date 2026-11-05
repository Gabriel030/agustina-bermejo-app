package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.CollectionsPolicySetsConfiguration;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFacts;
import com.red_escuela.backend.billing.collections.domain.ReminderPriorDebt;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewInstallmentWaitsForPriorDebtPolicyTest {
    private final NewInstallmentWaitsForPriorDebtPolicy policy = new NewInstallmentWaitsForPriorDebtPolicy();

    private static final ReminderInvoiceFact MARCH = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
    private static final ReminderInvoiceFact APRIL = ReminderInvoiceFacts.installment(2L, "2027-04-01", "2027-04-11");

    private PolicyVerdict evaluate(ReminderMessageType type, ReminderInvoiceFact invoice, ReminderInvoiceFact... listedDebt) {
        return policy.evaluateRule(new PriorDebtReminderContext(invoice, type, List.of(listedDebt)));
    }

    @ParameterizedTest
    @EnumSource(value = ReminderMessageType.class, names = {"AFTER_ISSUE_REMINDER", "BEFORE_DUE_REMINDER"})
    @Tag("D-347")
    void theNewInstallmentOwnNoticesWaitWhileAnEarlierInstallmentIsOpen(ReminderMessageType type) {
        PolicyVerdict verdict = evaluate(type, APRIL, MARCH, APRIL);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("NEW_INSTALLMENT_WAITS_FOR_PRIOR_DEBT");
    }

    @ParameterizedTest
    @EnumSource(value = ReminderMessageType.class, names = {"ISSUANCE_NOTICE", "AFTER_DUE_REMINDER", "BEFORE_SUSPENSION_REMINDER"})
    @Tag("D-347")
    void theOtherNoticeTypesAreNeverHeldBackByThePriorDebt(ReminderMessageType type) {
        assertThat(evaluate(type, APRIL, MARCH, APRIL).allowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ReminderMessageType.class, names = {"AFTER_ISSUE_REMINDER", "BEFORE_DUE_REMINDER"})
    @Tag("D-347")
    void withThePreviousInstallmentPaidTheNewOneKeepsItsNormalCadence(ReminderMessageType type) {
        assertThat(evaluate(type, APRIL, APRIL).allowed()).isTrue();
    }

    @Test
    @Tag("D-347")
    void anInstallmentIssuedTheSameDayIsNotPriorDebt() {
        ReminderInvoiceFact fee = ReminderInvoiceFacts.installment(3L, "2027-04-01", "2027-04-11");

        assertThat(ReminderPriorDebt.existsBefore(APRIL, List.of(APRIL, fee))).isFalse();
        assertThat(evaluate(ReminderMessageType.BEFORE_DUE_REMINDER, APRIL, APRIL, fee).allowed()).isTrue();
    }

    @Test
    @Tag("D-347")
    void anUnpaidLateFeeDebitNoteOfAnEarlierInvoiceCountsAsPriorDebt() {
        ReminderInvoiceFact note = ReminderInvoiceFacts.lateFeeDebitNote(9L, 1L, "2027-03-20", "2027-03-30");

        assertThat(ReminderPriorDebt.existsBefore(APRIL, List.of(note, APRIL))).isTrue();
    }

    @Test
    @Tag("D-347")
    void anInvoiceIssuedAfterTheNewOneIsNotPriorDebt() {
        ReminderInvoiceFact may = ReminderInvoiceFacts.installment(4L, "2027-05-01", "2027-05-11");

        assertThat(ReminderPriorDebt.existsBefore(APRIL, List.of(APRIL, may))).isFalse();
    }

    @Test
    @Tag("D-347")
    void thePriorDebtPolicySetListsThePolicyWithItsDecision() {
        var policySet = new CollectionsPolicySetsConfiguration().collectionReminderPriorDebtPolicySet();

        assertThat(policySet.policies()).extracting(listed -> listed.ruleName()).containsExactly("NewInstallmentWaitsForPriorDebtPolicy");
    }
}

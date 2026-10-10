package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineCredit;
import com.red_escuela.backend.billing.invoice.domain.InvoiceTestData;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.exception.CreditNoteDistributionMismatchException;
import com.red_escuela.backend.billing.note.exception.CreditNoteIssuingRejectedException;
import com.red_escuela.backend.billing.note.exception.CreditNoteJustificationRequiredException;
import com.red_escuela.backend.billing.note.exception.CreditNoteRefundExceedsPaidAmountException;
import com.red_escuela.backend.billing.note.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.billing.invoice.exception.CreditNoteExceedsCreditableAmountException;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.DUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-165")
@Tag("D-244")
@Tag("D-153")
@Tag("D-238")
class CreditNoteIssuingPoliciesTest {

    private static final String RECIPIENT = "30111222";

    private static Invoice unpaid() {
        return InvoiceTestData.withId(InvoiceTestData.issuedMonthly("80000.00"), 5L);
    }

    private static Invoice reducedByFortyThousandAndPaid() {
        Invoice invoice = unpaid();
        invoice.applyCreditNote(new InvoiceCreditNoteApplication(CreditNoteApplicationKind.DEBT_REDUCTION, ars("40000.00"), ars("0.00"),
                List.of(new InvoiceLineCredit(1, ars("40000.00")))), DUE_DATE, ACTOR);
        invoice.allocateFullInvoicePayment(ars("40000.00"), DUE_DATE, ACTOR);
        return invoice;
    }

    private static IssueCreditNoteCommand command(CreditNoteReason reason, String justification, CreditNoteDestination destination, String... amounts) {
        List<CreditNoteLineAmount> lines = new java.util.ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            lines.add(new CreditNoteLineAmount(i + 1, com.red_escuela.backend.shared.domain.Money.of(new BigDecimal(amounts[i]), com.red_escuela.backend.shared.domain.Currency.ARS)));
        }
        return new IssueCreditNoteCommand(5L, reason, justification, lines, destination, 9L, false, null);
    }

    private static CreditNoteIssuingContext context(IssueCreditNoteCommand command, Invoice invoice, String creditNoteRecipient) {
        return new CreditNoteIssuingContext(command, CreditedInvoiceFacts.of(invoice), creditNoteRecipient);
    }

    private static CreditNoteIssuingContext unpaidContext(String amount) {
        return context(command(CreditNoteReason.BILLING_ERROR, "Error", CreditNoteDestination.DEBT_OFFSET, amount), unpaid(), RECIPIENT);
    }

    private static CreditNoteIssuingContext paidContext(String amount) {
        return context(command(CreditNoteReason.BILLING_ERROR, "Error", CreditNoteDestination.FREE_CREDIT, amount), reducedByFortyThousandAndPaid(), RECIPIENT);
    }

    private static boolean allows(Policy<CreditNoteIssuingContext> policy, CreditNoteIssuingContext context) {
        return policy.evaluateRule(context).allowed();
    }

    private static String codeOf(Policy<CreditNoteIssuingContext> policy, CreditNoteIssuingContext context) {
        return policy.evaluateRule(context).code();
    }

    @Test
    void lineAmountWithinTheCreditableAmountOfTheLineIsAllowedUpToTheCent() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteLinesWithinCreditableAmountPolicy();

        assertThat(allows(policy, paidContext("40000.00"))).isTrue();
        assertThat(allows(policy, paidContext("40000.01"))).isFalse();
        assertThat(codeOf(policy, paidContext("40000.01"))).isEqualTo("CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT");
        assertThat(allows(policy, unpaidContext("80000.00"))).isTrue();
        assertThat(allows(policy, unpaidContext("80000.01"))).isFalse();
    }

    @Test
    void aLineOfTheCommandMissingFromTheInvoiceIsNotCreditable() {
        IssueCreditNoteCommand onLineTwo = new IssueCreditNoteCommand(5L, CreditNoteReason.BILLING_ERROR, "x",
                List.of(new CreditNoteLineAmount(2, com.red_escuela.backend.shared.domain.Money.of(new BigDecimal("1.00"), com.red_escuela.backend.shared.domain.Currency.ARS))), CreditNoteDestination.DEBT_OFFSET, 9L, false, null);

        assertThat(allows(new CreditNoteLinesWithinCreditableAmountPolicy(), context(onLineTwo, unpaid(), RECIPIENT))).isFalse();
    }

    @Test
    void offsetCannotExceedWhatTheInvoiceHasUnpaid() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteWithinOutstandingAmountPolicy();

        assertThat(allows(policy, unpaidContext("80000.00"))).isTrue();
        assertThat(allows(policy, unpaidContext("80000.01"))).isFalse();
        assertThat(codeOf(policy, unpaidContext("80000.01"))).isEqualTo("CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT");
        assertThat(allows(policy, paidContext("999999.00"))).isTrue();
    }

    @Test
    void freeCreditCannotExceedWhatWasPaid() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteRefundWithinPaidAmountPolicy();

        assertThat(allows(policy, paidContext("40000.00"))).isTrue();
        assertThat(allows(policy, paidContext("40000.01"))).isFalse();
        assertThat(codeOf(policy, paidContext("40000.01"))).isEqualTo("CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT");
        assertThat(allows(policy, unpaidContext("999999.00"))).isTrue();
    }

    @Test
    void freeCreditAlreadyGivenCountsAgainstWhatWasPaid() {
        Invoice invoice = reducedByFortyThousandAndPaid();
        invoice.applyCreditNote(new InvoiceCreditNoteApplication(CreditNoteApplicationKind.DEBT_REDUCTION, ars("0.00"), ars("30000.00"),
                List.of(new InvoiceLineCredit(1, ars("30000.00")))), DUE_DATE, ACTOR);
        Policy<CreditNoteIssuingContext> policy = new CreditNoteRefundWithinPaidAmountPolicy();

        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.FREE_CREDIT, "10000.00"), invoice, RECIPIENT))).isTrue();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.FREE_CREDIT, "10000.01"), invoice, RECIPIENT))).isFalse();
    }

    @Test
    void destinationMustMatchTheCurrentStateOfTheInvoice() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteDestinationUnchangedPolicy();

        assertThat(allows(policy, unpaidContext("1.00"))).isTrue();
        assertThat(allows(policy, paidContext("1.00"))).isTrue();
        CreditNoteIssuingContext paidMeanwhile = context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"),
                reducedByFortyThousandAndPaid(), RECIPIENT);
        assertThat(allows(policy, paidMeanwhile)).isFalse();
        assertThat(codeOf(policy, paidMeanwhile)).isEqualTo("CREDIT_NOTE_DISTRIBUTION_MISMATCH");
    }

    @Test
    void justificationIsRequiredAndLimitedToAThousandCharacters() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteJustificationRequiredPolicy();
        Invoice invoice = unpaid();

        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "a".repeat(1000), CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isTrue();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "a".repeat(1001), CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isFalse();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "   ", CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isFalse();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, null, CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isFalse();
        assertThat(codeOf(policy, context(command(CreditNoteReason.BILLING_ERROR, null, CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT)))
                .isEqualTo("CREDIT_NOTE_JUSTIFICATION_REQUIRED");
    }

    @Test
    void onlyBillingErrorAndLateFeeAnnulmentAreAllowedAsUserReasons() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteReasonAllowedPolicy();
        Invoice invoice = unpaid();

        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isTrue();
        assertThat(allows(policy, context(command(CreditNoteReason.LATE_FEE_ANNULMENT, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isTrue();
        assertThat(allows(policy, context(command(CreditNoteReason.WITHDRAWAL, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isFalse();
        assertThat(allows(policy, context(command(CreditNoteReason.CANCELLATION, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), invoice, RECIPIENT))).isFalse();
        assertThat(CreditNoteReason.valueOf("BILLING_ERROR")).isNotNull();
        assertThat(CreditNoteReason.values()).extracting(Enum::name).doesNotContain("RETROACTIVE_SCHOLARSHIP");
    }

    @Test
    void theCreditNoteGoesToTheSameRecipientAsTheInvoice() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteRecipientMatchesInvoicePolicy();

        assertThat(allows(policy, unpaidContext("1.00"))).isTrue();
        CreditNoteIssuingContext other = context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), unpaid(), "99999999");
        assertThat(allows(policy, other)).isFalse();
        assertThat(codeOf(policy, other)).isEqualTo("CREDIT_NOTE_RECIPIENT_MISMATCH");
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), unpaid(), null))).isFalse();
    }

    @Test
    void theCreditNoteNeedsAnIssuedInvoiceAsReference() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteReferencesIssuedInvoicePolicy();

        assertThat(allows(policy, unpaidContext("1.00"))).isTrue();
        CreditNoteIssuingContext withoutInvoice = new CreditNoteIssuingContext(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), null, RECIPIENT);
        assertThat(allows(policy, withoutInvoice)).isFalse();
        assertThat(codeOf(policy, withoutInvoice)).isEqualTo("CREDIT_NOTE_INVOICE_REFERENCE_REQUIRED");
    }

    @Test
    void anInvoiceUnderReviewDoesNotAcceptCreditNotes() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteInvoiceNotUnderReviewPolicy();
        Invoice underReview = unpaid();
        underReview.markPaymentUnderReview(DUE_DATE, ACTOR, "comprobante");

        assertThat(allows(policy, unpaidContext("1.00"))).isTrue();
        CreditNoteIssuingContext context = context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00"), underReview, RECIPIENT);
        assertThat(allows(policy, context)).isFalse();
        assertThat(codeOf(policy, context)).isEqualTo("PENDING_BANK_TRANSFER_ON_INVOICE");
    }

    @Test
    void linesMustExistBePositiveAndNotRepeat() {
        Policy<CreditNoteIssuingContext> policy = new CreditNoteLinesBelongToInvoicePolicy();
        Invoice invoice = unpaid();

        assertThat(allows(policy, unpaidContext("1.00"))).isTrue();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET), invoice, RECIPIENT))).isFalse();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "0.00"), invoice, RECIPIENT))).isFalse();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "-1.00"), invoice, RECIPIENT))).isFalse();
        assertThat(allows(policy, context(command(CreditNoteReason.BILLING_ERROR, "x", CreditNoteDestination.DEBT_OFFSET, "1.00", "1.00"), invoice, RECIPIENT))).isFalse();
        IssueCreditNoteCommand repeated = new IssueCreditNoteCommand(5L, CreditNoteReason.BILLING_ERROR, "x",
                List.of(new CreditNoteLineAmount(1, com.red_escuela.backend.shared.domain.Money.of(BigDecimal.ONE, com.red_escuela.backend.shared.domain.Currency.ARS)), new CreditNoteLineAmount(1, com.red_escuela.backend.shared.domain.Money.of(BigDecimal.ONE, com.red_escuela.backend.shared.domain.Currency.ARS))), CreditNoteDestination.DEBT_OFFSET, 9L, false, null);
        assertThat(allows(policy, context(repeated, invoice, RECIPIENT))).isFalse();
    }

    @Test
    void everyPolicyNamesItselfInItsVerdicts() {
        List<Policy<CreditNoteIssuingContext>> policies = List.of(new CreditNoteLinesBelongToInvoicePolicy(), new CreditNoteRefundWithinPaidAmountPolicy(),
                new CreditNoteWithinOutstandingAmountPolicy(), new CreditNoteLinesWithinCreditableAmountPolicy(), new CreditNoteJustificationRequiredPolicy(),
                new CreditNoteReasonAllowedPolicy(), new CreditNoteRecipientMatchesInvoicePolicy(), new CreditNoteReferencesIssuedInvoicePolicy(),
                new CreditNoteDestinationUnchangedPolicy(), new CreditNoteInvoiceNotUnderReviewPolicy());

        policies.forEach(policy -> assertThat(policy.ruleName()).isEqualTo(policy.getClass().getSimpleName()));
    }

    @Test
    void everyViolationCodeMapsToItsSpecificException() {
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT"))).isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT"))).isInstanceOf(CreditNoteRefundExceedsPaidAmountException.class);
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("PENDING_BANK_TRANSFER_ON_INVOICE"))).isInstanceOf(PendingBankTransferOnInvoiceException.class);
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("CREDIT_NOTE_DISTRIBUTION_MISMATCH"))).isInstanceOf(CreditNoteDistributionMismatchException.class);
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("CREDIT_NOTE_JUSTIFICATION_REQUIRED"))).isInstanceOf(CreditNoteJustificationRequiredException.class);
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("INVALID_CREDIT_NOTE_REASON"))).isInstanceOf(InvalidCreditNoteReasonException.class);
        assertThat(CreditNoteIssuingRejections.rejectionOf(violation("CREDIT_NOTE_RECIPIENT_MISMATCH"))).isInstanceOf(CreditNoteIssuingRejectedException.class);
    }

    private static List<PolicyViolation> violation(String code) {
        return List.of(new PolicyViolation(code, "reason"));
    }
}

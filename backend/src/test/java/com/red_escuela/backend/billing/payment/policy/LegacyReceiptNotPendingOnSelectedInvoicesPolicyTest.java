package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-260")
class LegacyReceiptNotPendingOnSelectedInvoicesPolicyTest {
    private final LegacyReceiptNotPendingOnSelectedInvoicesPolicy policy = new LegacyReceiptNotPendingOnSelectedInvoicesPolicy();

    @Test
    void deniesWhenALegacyReceiptIsPendingOnASelectedInvoice() {
        var verdict = policy.evaluateRule(new BankTransferReceiptRegistrationCheck(ACCOUNT, Currency.ARS, List.of(), List.of(), false, true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("LEGACY_RECEIPT_PENDING_ON_INVOICE");
    }

    @Test
    void allowsWhenNoLegacyReceiptIsPending() {
        assertThat(policy.evaluateRule(new BankTransferReceiptRegistrationCheck(ACCOUNT, Currency.ARS, List.of(), List.of(), false, false)).allowed()).isTrue();
    }
}

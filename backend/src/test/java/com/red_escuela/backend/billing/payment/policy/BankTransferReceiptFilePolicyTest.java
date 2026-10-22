package com.red_escuela.backend.billing.payment.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-267")
class BankTransferReceiptFilePolicyTest {
    private final BankTransferReceiptFilePolicy policy = new BankTransferReceiptFilePolicy();

    @Test
    void acceptsEachAllowedTypeWhenTheContentMatches() {
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck("application/pdf", "application/pdf")).allowed()).isTrue();
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck("image/png", "image/png")).allowed()).isTrue();
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck("image/jpeg", "image/jpeg")).allowed()).isTrue();
    }

    @Test
    void rejectsADeclaredTypeOutsideTheAllowedOnes() {
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck("text/html", "application/pdf")).code()).isEqualTo("BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED");
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck(null, "application/pdf")).code()).isEqualTo("BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void rejectsAContentThatDoesNotMatchTheDeclaredType() {
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck("application/pdf", null)).code()).isEqualTo("BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH");
        assertThat(policy.evaluateRule(new BankTransferReceiptFileCheck("application/pdf", "image/png")).code()).isEqualTo("BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH");
    }
}

package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-269")
class DuplicateBankTransferPolicyTest {
    private final DuplicateBankTransferPolicy policy = new DuplicateBankTransferPolicy();

    @Test
    void deniesWhenTheSameTransferAlreadyExists() {
        var verdict = policy.evaluateRule(new BankTransferReceiptRegistrationCheck(ACCOUNT, Currency.ARS, List.of(), List.of(), true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("POSSIBLE_DUPLICATE_BANK_TRANSFER");
    }

    @Test
    void allowsWhenNoSuchTransferExists() {
        assertThat(policy.evaluateRule(new BankTransferReceiptRegistrationCheck(ACCOUNT, Currency.ARS, List.of(), List.of(), false)).allowed()).isTrue();
    }
}

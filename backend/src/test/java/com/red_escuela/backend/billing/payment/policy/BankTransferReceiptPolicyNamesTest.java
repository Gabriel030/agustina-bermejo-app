package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.billing.payment.domain.ReceiptProtectionFacts;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-267")
@Tag("D-269")
@Tag("D-153")
@Tag("D-260")
@Tag("D-154")
class BankTransferReceiptPolicyNamesTest {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 10);

    @Test
    void everyVerdictCarriesTheNameOfThePolicyThatIssuedIt() {
        BankTransferReceiptDetailsRequiredPolicy details = new BankTransferReceiptDetailsRequiredPolicy();
        Money amount = Money.of(new BigDecimal("1.00"), Currency.ARS);
        BankTransferReceiptDetailsCheck validDetails = new BankTransferReceiptDetailsCheck(amount, DUE, DUE, "OP", "CBU", "Ana", null, true, 1);
        BankTransferReceiptDetailsCheck invalidDetails = new BankTransferReceiptDetailsCheck(null, DUE, DUE, "OP", "CBU", "Ana", null, true, 1);
        assertThat(details.ruleName()).isEqualTo("BankTransferReceiptDetailsRequiredPolicy");
        assertThat(details.evaluateRule(validDetails).policy()).isEqualTo("BankTransferReceiptDetailsRequiredPolicy");
        assertThat(details.evaluateRule(invalidDetails).policy()).isEqualTo("BankTransferReceiptDetailsRequiredPolicy");

        ReceiptInvoiceFact pending = new ReceiptInvoiceFact(1L, 7L, Currency.ARS, true, InvoicePaymentStatus.PENDING, DUE, amount);
        ReceiptInvoiceFact foreign = new ReceiptInvoiceFact(2L, 8L, Currency.ARS, true, InvoicePaymentStatus.PENDING, DUE, amount);
        BankTransferReceiptRegistrationCheck allowedRegistration = new BankTransferReceiptRegistrationCheck(7L, Currency.ARS, List.of(pending), List.of(pending), false);
        BankTransferReceiptRegistrationCheck deniedRegistration = new BankTransferReceiptRegistrationCheck(7L, Currency.ARS, List.of(foreign), List.of(pending), true);

        SelectedInvoicesBelongToAccountPolicy belongs = new SelectedInvoicesBelongToAccountPolicy();
        assertThat(belongs.ruleName()).isEqualTo("SelectedInvoicesBelongToAccountPolicy");
        assertThat(belongs.evaluateRule(allowedRegistration).policy()).isEqualTo("SelectedInvoicesBelongToAccountPolicy");
        assertThat(belongs.evaluateRule(deniedRegistration).policy()).isEqualTo("SelectedInvoicesBelongToAccountPolicy");

        DuplicateBankTransferPolicy duplicate = new DuplicateBankTransferPolicy();
        assertThat(duplicate.ruleName()).isEqualTo("DuplicateBankTransferPolicy");
        assertThat(duplicate.evaluateRule(allowedRegistration).policy()).isEqualTo("DuplicateBankTransferPolicy");
        assertThat(duplicate.evaluateRule(deniedRegistration).policy()).isEqualTo("DuplicateBankTransferPolicy");

        ReceiptCoversOldestInvoicesFirstPolicy oldestFirst = new ReceiptCoversOldestInvoicesFirstPolicy();
        assertThat(oldestFirst.ruleName()).isEqualTo("ReceiptCoversOldestInvoicesFirstPolicy");
        assertThat(oldestFirst.evaluateRule(allowedRegistration).policy()).isEqualTo("ReceiptCoversOldestInvoicesFirstPolicy");
        assertThat(oldestFirst.evaluateRule(deniedRegistration).policy()).isEqualTo("ReceiptCoversOldestInvoicesFirstPolicy");

        ReceiptUploadedByDueDatePolicy byDueDate = new ReceiptUploadedByDueDatePolicy();
        assertThat(byDueDate.ruleName()).isEqualTo("ReceiptUploadedByDueDatePolicy");
        assertThat(byDueDate.evaluateRule(new ReceiptProtectionCheck(new ReceiptProtectionFacts(InvoicePaymentStatus.PENDING, DUE, DUE, DUE))).policy())
                .isEqualTo("ReceiptUploadedByDueDatePolicy");
        assertThat(byDueDate.evaluateRule(new ReceiptProtectionCheck(new ReceiptProtectionFacts(InvoicePaymentStatus.OVERDUE, DUE, DUE, DUE))).policy())
                .isEqualTo("ReceiptUploadedByDueDatePolicy");
    }
}

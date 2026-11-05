package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.exception.BankTransferDetailsRequiredException;
import com.red_escuela.backend.billing.payment.exception.InvoiceAlreadyUnderReviewException;
import com.red_escuela.backend.billing.payment.exception.PossibleDuplicateBankTransferException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceCurrencyMismatchException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceNotPayableException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceOfAnotherAccountException;
import com.red_escuela.backend.billing.payment.exception.ReceiptMustCoverOldestInvoicesFirstException;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-267")
@Tag("D-269")
@Tag("D-153")
@Tag("D-260")
class BankTransferReceiptRejectionsTest {

    private static RuntimeException rejectionOfCode(String code) {
        return BankTransferReceiptRejections.rejectionOf(List.of(new PolicyViolation(code, "motivo")));
    }

    @ParameterizedTest
    @CsvSource({"BANK_TRANSFER_AMOUNT_INVALID,BankTransferDetailsRequiredException,BANK_TRANSFER_DETAILS_REQUIRED",
            "BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED,BankTransferDetailsRequiredException,BANK_TRANSFER_DETAILS_REQUIRED",
            "INVOICE_CURRENCY_MISMATCH,ReceiptInvoiceCurrencyMismatchException,RECEIPT_INVOICE_CURRENCY_MISMATCH",
            "INVOICE_OF_ANOTHER_ACCOUNT,ReceiptInvoiceOfAnotherAccountException,RECEIPT_INVOICE_OF_ANOTHER_ACCOUNT",
            "INVOICE_NOT_PAYABLE_BY_RECEIPT,ReceiptInvoiceNotPayableException,RECEIPT_INVOICE_NOT_PAYABLE",
            "INVOICE_ALREADY_UNDER_REVIEW,InvoiceAlreadyUnderReviewException,INVOICE_ALREADY_UNDER_REVIEW",
            "RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST,ReceiptMustCoverOldestInvoicesFirstException,RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST",
            "POSSIBLE_DUPLICATE_BANK_TRANSFER,PossibleDuplicateBankTransferException,POSSIBLE_DUPLICATE_BANK_TRANSFER",
            "LEGACY_RECEIPT_PENDING_ON_INVOICE,LegacyReceiptPendingOnInvoiceException,LEGACY_RECEIPT_PENDING_ON_INVOICE",
            "BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH,BankTransferDetailsRequiredException,BANK_TRANSFER_DETAILS_REQUIRED",
            "BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED,BankTransferDetailsRequiredException,BANK_TRANSFER_DETAILS_REQUIRED"})
    void everyViolationMapsToItsExceptionAndItsHttpCode(String violationCode, String exceptionName, String httpCode) {
        RuntimeException rejection = rejectionOfCode(violationCode);

        assertThat(rejection.getClass().getSimpleName()).isEqualTo(exceptionName);
        assertThat(ErrorCodeResponse.of(rejection).code()).isEqualTo(httpCode);
        assertThat(((ReportsPolicyViolations) rejection).violations()).extracting(PolicyViolation::code).containsExactly(violationCode);
    }

    @Test
    void theExceptionsAreOfTheKindThatMapsToTheExpectedStatus() {
        assertThat(rejectionOfCode("BANK_TRANSFER_HOLDER_NAME_REQUIRED")).isInstanceOf(BankTransferDetailsRequiredException.class);
        assertThat(rejectionOfCode("INVOICE_CURRENCY_MISMATCH")).isInstanceOf(ReceiptInvoiceCurrencyMismatchException.class);
        assertThat(rejectionOfCode("INVOICE_OF_ANOTHER_ACCOUNT")).isInstanceOf(ReceiptInvoiceOfAnotherAccountException.class);
        assertThat(rejectionOfCode("INVOICE_NOT_PAYABLE_BY_RECEIPT")).isInstanceOf(ReceiptInvoiceNotPayableException.class);
        assertThat(rejectionOfCode("INVOICE_ALREADY_UNDER_REVIEW")).isInstanceOf(InvoiceAlreadyUnderReviewException.class);
        assertThat(rejectionOfCode("RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST")).isInstanceOf(ReceiptMustCoverOldestInvoicesFirstException.class);
    }

    @Test
    void anUnmappedViolationIsAProgrammingError() {
        assertThatThrownBy(() -> rejectionOfCode("SOMETHING_ELSE")).isInstanceOf(IllegalStateException.class).hasMessageContaining("SOMETHING_ELSE");
    }

    @Test
    void aDuplicateDetectedByTheUniqueIndexIsTheSameConflictAsThePolicy() {
        RuntimeException rejection = BankTransferReceiptRejections.duplicateDetectedByUniqueIndex();

        assertThat(rejection).isInstanceOf(PossibleDuplicateBankTransferException.class);
        assertThat(((ReportsPolicyViolations) rejection).violations()).extracting(PolicyViolation::code).containsExactly("POSSIBLE_DUPLICATE_BANK_TRANSFER");
    }

    @Test
    void theRegistrationCheckKeepsItsListsAsCopies() {
        ArrayList<ReceiptInvoiceFact> selected = new ArrayList<>();
        BankTransferReceiptRegistrationCheck check = new BankTransferReceiptRegistrationCheck(1L, Currency.ARS, selected, selected, false);
        selected.add(null);

        assertThat(check.selectedInvoices()).isEmpty();
        assertThat(check.openInvoicesOfAccount()).isEmpty();
    }
}

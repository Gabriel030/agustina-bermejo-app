package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.api.AdmissionReceiptLookup;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionReceiptLookupIT extends AbstractBankTransferReceiptIT {

    @Autowired
    AdmissionReceiptLookup lookup;
    @Autowired
    BankTransferReceiptRepository receiptRepository;

    @Test
    @Tag("D-153")
    void admissionSummaryRecognizesReceiptThroughCheckoutItems() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        Long may = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 5, 10));
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(march, april), "160000.00", TODAY, "OP-LOOKUP"));

        var summaries = lookup.findLatestReceiptByInvoiceIds(List.of(march, april, may));

        assertThat(summaries).containsOnlyKeys(march, april);
        assertThat(summaries.get(april).status()).isEqualTo("PENDING_REVIEW");
        assertThat(summaries.get(march).status()).isEqualTo("PENDING_REVIEW");
        assertThat(receiptRepository.existsByCheckoutItemInvoiceIdAndStatus(april, BankTransferReceiptStatus.PENDING_REVIEW)).isTrue();
        assertThat(receiptRepository.existsByCheckoutItemInvoiceIdAndStatus(may, BankTransferReceiptStatus.PENDING_REVIEW)).isFalse();
        assertThat(receiptRepository.existsByCheckoutItemInvoiceIdAndStatus(april, BankTransferReceiptStatus.APPROVED)).isFalse();
        assertThat(submitted.items()).hasSize(2);
    }

    @Test
    @Tag("D-153")
    void theMostRecentReceiptWinsBetweenTheLegacyAndTheNewFlow() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-LOOKUP-NEW"));
        jdbc.update("update bank_transfer_receipt set status = 'REJECTED', uploaded_at = ?", java.sql.Timestamp.valueOf(LocalDate.of(2027, 3, 1).atStartOfDay()));
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'legacy.pdf', ?, 'PENDING_REVIEW', 0)", march,
                java.sql.Timestamp.valueOf(LocalDate.of(2027, 3, 8).atStartOfDay()));

        var summaries = lookup.findLatestReceiptByInvoiceIds(List.of(march));

        assertThat(summaries.get(march).status()).isEqualTo("PENDING_REVIEW");
    }
}

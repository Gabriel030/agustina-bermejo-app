package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptRecord;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface BankTransferReceiptRecordRepository extends Repository<BankTransferReceiptRecord, Long> {

    BankTransferReceiptRecord save(BankTransferReceiptRecord receipt);

    @Query(value = """
            select exists (select 1 from bank_transfer_receipt
                           where invoice_id in (:invoiceIds) and status = 'PENDING_REVIEW' and payment_id is null)
            """, nativeQuery = true)
    boolean existsLegacyPendingReceiptOnAnyInvoice(@Param("invoiceIds") Collection<Long> invoiceIds);
}

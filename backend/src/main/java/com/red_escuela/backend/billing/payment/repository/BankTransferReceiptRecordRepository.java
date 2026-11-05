package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptRecord;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BankTransferReceiptRecordRepository extends Repository<BankTransferReceiptRecord, Long> {

    BankTransferReceiptRecord save(BankTransferReceiptRecord receipt);

    @Query(value = """
            select exists (select 1 from bank_transfer_receipt
                           where invoice_id in (:invoiceIds) and status = 'PENDING_REVIEW' and payment_id is null)
            """, nativeQuery = true)
    boolean existsLegacyPendingReceiptOnAnyInvoice(@Param("invoiceIds") Collection<Long> invoiceIds);

    interface ReceiptAwaitingReviewAlert {
        Long getReceiptId();

        Long getPaymentId();

        Long getInvoiceId();

        LocalDateTime getUploadedAt();
    }

    interface ReceiptSubmissionFacts {
        Long getReceiptId();

        String getStatus();

        LocalDateTime getUploadedAt();

        BigDecimal getDeclaredAmount();

        String getDeclaredAmountCurrency();

        LocalDate getTransferDate();

        String getOperationNumber();

        String getOriginAccount();

        String getHolderName();

        LocalDateTime getReviewAlertSentAt();
    }

    @Query(value = """
            select id as receiptId, payment_id as paymentId, invoice_id as invoiceId, uploaded_at as uploadedAt
            from bank_transfer_receipt
            where status = 'PENDING_REVIEW' and review_alert_sent_at is null and uploaded_at < :uploadedBefore
            order by uploaded_at, id
            """, nativeQuery = true)
    List<ReceiptAwaitingReviewAlert> findPendingWithoutReviewAlertUploadedBefore(@Param("uploadedBefore") LocalDateTime uploadedBefore);

    @Modifying
    @Query(value = """
            update bank_transfer_receipt set review_alert_sent_at = :sentAt
            where id = :receiptId and status = 'PENDING_REVIEW' and review_alert_sent_at is null
            """, nativeQuery = true)
    int markReviewAlertSent(@Param("receiptId") Long receiptId, @Param("sentAt") LocalDateTime sentAt);

    @Query(value = """
            select id as receiptId, status as status, uploaded_at as uploadedAt, declared_amount as declaredAmount, declared_amount_currency as declaredAmountCurrency, transfer_date as transferDate,
                   operation_number as operationNumber, origin_account as originAccount, holder_name as holderName, review_alert_sent_at as reviewAlertSentAt
            from bank_transfer_receipt where id in (:receiptIds)
            """, nativeQuery = true)
    List<ReceiptSubmissionFacts> findSubmissionFactsByReceiptIds(@Param("receiptIds") Collection<Long> receiptIds);
}

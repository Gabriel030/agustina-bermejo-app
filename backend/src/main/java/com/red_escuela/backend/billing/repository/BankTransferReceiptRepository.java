package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BankTransferReceiptRepository extends JpaRepository<BankTransferReceipt, Long> {

    @Query("""
            select distinct r from BankTransferReceipt r
            join fetch r.invoice i
            join fetch i.lines
            left join fetch r.uploadedBy
            where r.status = :status
            order by r.uploadedAt asc, r.id asc
            """)
    List<BankTransferReceipt> findByStatusWithStudentDetails(@Param("status") BankTransferReceiptStatus status);

    @Query("""
            select r from BankTransferReceipt r
            left join fetch r.uploadedBy
            left join fetch r.reviewedBy
            where r.invoice.id in :invoiceIds
            order by r.uploadedAt desc, r.id desc
            """)
    List<BankTransferReceipt> findByInvoiceIdsWithPeople(@Param("invoiceIds") Collection<Long> invoiceIds);

    interface ReceiptOfCheckoutInvoice {
        Long getInvoiceId();

        BankTransferReceipt getReceipt();
    }

    @Query("""
            select i.invoiceId as invoiceId, r as receipt from BankTransferReceipt r, PaymentCheckoutItem i
            where r.paymentId = i.paymentId and i.invoiceId in :invoiceIds
            order by r.uploadedAt desc, r.id desc
            """)
    List<ReceiptOfCheckoutInvoice> findByCheckoutItemInvoiceIds(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("""
            select count(r) > 0 from BankTransferReceipt r, PaymentCheckoutItem i
            where r.paymentId = i.paymentId and i.invoiceId = :invoiceId and r.status = :status
            """)
    boolean existsByCheckoutItemInvoiceIdAndStatus(@Param("invoiceId") Long invoiceId, @Param("status") BankTransferReceiptStatus status);

    List<BankTransferReceipt> findByStatusOrderByUploadedAtAsc(BankTransferReceiptStatus status);

    boolean existsByInvoiceIdAndStatus(Long invoiceId, BankTransferReceiptStatus status);

    @Query("""
            select r from BankTransferReceipt r, StudentCurrentAccount a
            where a.id = r.invoice.studentCurrentAccountId and a.studentId = :studentId
            order by r.uploadedAt desc
            """)
    List<BankTransferReceipt> findByStudentId(@Param("studentId") Long studentId);
}

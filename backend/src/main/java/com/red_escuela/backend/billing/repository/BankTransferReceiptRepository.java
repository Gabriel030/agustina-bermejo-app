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

    List<BankTransferReceipt> findByStatusOrderByUploadedAtAsc(BankTransferReceiptStatus status);

    boolean existsByInvoiceIdAndStatus(Long invoiceId, BankTransferReceiptStatus status);

    @Query("""
            select r from BankTransferReceipt r, StudentCurrentAccount a
            where a.id = r.invoice.studentCurrentAccountId and a.studentId = :studentId
            order by r.uploadedAt desc
            """)
    List<BankTransferReceipt> findByStudentId(@Param("studentId") Long studentId);
}

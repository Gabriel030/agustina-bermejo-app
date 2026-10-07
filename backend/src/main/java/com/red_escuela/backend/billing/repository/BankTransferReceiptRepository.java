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
            select r from BankTransferReceipt r
            join fetch r.invoice i
            left join fetch i.courseEnrollment ce
            left join fetch ce.student ces
            left join fetch ces.person cep
            left join fetch i.programEnrollment pe
            left join fetch pe.student pes
            left join fetch pes.person pep
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
            select r from BankTransferReceipt r
            left join r.invoice.courseEnrollment ce
            left join r.invoice.programEnrollment pe
            where ce.student.id = :studentId
               or pe.student.id = :studentId
            order by r.uploadedAt desc
            """)
    List<BankTransferReceipt> findByStudentId(@Param("studentId") Long studentId);
}

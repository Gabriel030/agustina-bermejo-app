package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    List<PaymentReceipt> findByStatusOrderByUploadedAtAsc(PaymentReceiptStatus status);

    boolean existsByInvoiceIdAndStatus(Long invoiceId, PaymentReceiptStatus status);

    @Query("""
            select r from PaymentReceipt r
            left join r.invoice.courseEnrollment ce
            left join r.invoice.programEnrollment pe
            where ce.student.id = :studentId
               or pe.student.id = :studentId
            order by r.uploadedAt desc
            """)
    List<PaymentReceipt> findByStudentId(@Param("studentId") Long studentId);
}

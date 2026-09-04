package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    List<PaymentReceipt> findByStatusOrderByUploadedAtAsc(PaymentReceiptStatus status);

    // Evita duplicados: una cuota en revisión no admite un segundo comprobante.
    boolean existsByInvoiceIdAndStatus(Long invoiceId, PaymentReceiptStatus status);

    // Autoservicio del alumno: sus propios comprobantes, sin importar el estado — Invoice no
    // tiene un student directo, hay que llegar vía courseEnrollment o programEnrollment.
    @Query("""
            select r from PaymentReceipt r
            where r.invoice.courseEnrollment.student.id = :studentId
               or r.invoice.programEnrollment.student.id = :studentId
            order by r.uploadedAt desc
            """)
    List<PaymentReceipt> findByStudentId(@Param("studentId") Long studentId);
}

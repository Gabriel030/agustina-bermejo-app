package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentApplicationRepository extends JpaRepository<CourseEnrollmentApplication, Long> {

    @Modifying(flushAutomatically = true)
    @Query("update CourseEnrollmentApplication a set a.paymentReceipt = (select r from BankTransferReceipt r where r.id = :receiptId) where a.id = :applicationId")
    int linkPaymentReceipt(@Param("applicationId") Long applicationId, @Param("receiptId") Long receiptId);

    Optional<CourseEnrollmentApplication> findByPaymentReceiptId(Long paymentReceiptId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from CourseEnrollmentApplication a where a.invoiceId = :invoiceId and a.courseEnrollment is null and a.cancelledAt is null order by a.id desc")
    List<CourseEnrollmentApplication> findLockedPendingByInvoiceId(@org.springframework.data.repository.query.Param("invoiceId") Long invoiceId);

    @org.springframework.data.jpa.repository.Query("select count(a) > 0 from CourseEnrollmentApplication a where a.invoiceId = :invoiceId and a.courseEnrollment is null and a.cancelledAt is null")
    boolean existsPendingByInvoiceId(@org.springframework.data.repository.query.Param("invoiceId") Long invoiceId);

    List<CourseEnrollmentApplication> findByStudentIdOrderByIdDesc(Long studentId);

    boolean existsByStudentIdAndCourseIdAndPaymentReceiptStatus(Long studentId, Long courseId, BankTransferReceiptStatus status);
}

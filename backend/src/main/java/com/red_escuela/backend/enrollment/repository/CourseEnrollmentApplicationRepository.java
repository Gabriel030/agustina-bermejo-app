package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentApplicationRepository extends JpaRepository<CourseEnrollmentApplication, Long> {

    Optional<CourseEnrollmentApplication> findByPaymentReceiptId(Long paymentReceiptId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from CourseEnrollmentApplication a where a.invoice.id = :invoiceId and a.courseEnrollment is null order by a.id desc")
    List<CourseEnrollmentApplication> findLockedPendingByInvoiceId(@org.springframework.data.repository.query.Param("invoiceId") Long invoiceId);

    List<CourseEnrollmentApplication> findByStudentIdOrderByIdDesc(Long studentId);

    boolean existsByStudentIdAndCourseIdAndPaymentReceiptStatus(Long studentId, Long courseId, BankTransferReceiptStatus status);
}

package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentApplicationRepository extends JpaRepository<CourseEnrollmentApplication, Long> {

    Optional<CourseEnrollmentApplication> findByPaymentReceiptId(Long paymentReceiptId);

    List<CourseEnrollmentApplication> findByStudentIdOrderByIdDesc(Long studentId);

    boolean existsByStudentIdAndCourseIdAndPaymentReceiptStatus(Long studentId, Long courseId, BankTransferReceiptStatus status);
}

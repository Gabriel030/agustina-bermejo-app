package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentApplicationRepository extends JpaRepository<CourseEnrollmentApplication, Long> {

    Optional<CourseEnrollmentApplication> findByPaymentReceiptId(Long paymentReceiptId);

    List<CourseEnrollmentApplication> findByStudentIdOrderByIdDesc(Long studentId);
}

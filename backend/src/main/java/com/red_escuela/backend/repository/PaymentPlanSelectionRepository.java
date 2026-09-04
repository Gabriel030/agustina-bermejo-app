package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PaymentPlanSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface PaymentPlanSelectionRepository extends JpaRepository<PaymentPlanSelection, Long> {

    Optional<PaymentPlanSelection> findByCourseEnrollmentIdAndEffectiveToIsNull(Long courseEnrollmentId);

    Optional<PaymentPlanSelection> findByProgramEnrollmentIdAndEffectiveToIsNull(Long programEnrollmentId);

    List<PaymentPlanSelection> findByCourseEnrollmentIdOrderByEffectiveFrom(Long courseEnrollmentId);

    List<PaymentPlanSelection> findByProgramEnrollmentIdOrderByEffectiveFrom(Long programEnrollmentId);

    @Query("""
            select s from PaymentPlanSelection s
            where s.courseEnrollment.id = :courseEnrollmentId
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
            """)
    Optional<PaymentPlanSelection> findActiveForCourseEnrollment(@Param("courseEnrollmentId") Long courseEnrollmentId, @Param("month") YearMonth month);

    @Query("""
            select s from PaymentPlanSelection s
            where s.programEnrollment.id = :programEnrollmentId
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
            """)
    Optional<PaymentPlanSelection> findActiveForProgramEnrollment(@Param("programEnrollmentId") Long programEnrollmentId, @Param("month") YearMonth month);
}

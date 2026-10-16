package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.data.domain.Pageable;

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

    @Query("""
            select distinct s.programEnrollment.id from PaymentPlanSelection s
            where s.programEnrollment is not null
              and s.paymentPlanOption.cadence = :cadence
              and s.programEnrollment.status in :statuses
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
              and s.programEnrollment.id > :lastId
            order by s.programEnrollment.id
            """)
    List<Long> findProgramEnrollmentIdsWithActivePlan(@Param("cadence") PaymentPlanCadence cadence,
                                                       @Param("statuses") List<ProgramEnrollmentStatus> statuses,
                                                       @Param("month") YearMonth month, @Param("lastId") Long lastId, Pageable pageable);

    @Query("""
            select s.programEnrollment.id, s.effectiveFrom, s.scopeMonths from PaymentPlanSelection s
            where s.programEnrollment is not null
              and s.paymentPlanOption.cadence = com.red_escuela.backend.billing.entity.PaymentPlanCadence.ANNUAL_UPFRONT
              and s.programEnrollment.status in :statuses
              and s.scopeMonths is not null
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
              and s.programEnrollment.id > :lastId
            order by s.programEnrollment.id
            """)
    List<Object[]> findAnnualUpfrontSelectionsOfProgramEnrollments(@Param("statuses") List<ProgramEnrollmentStatus> statuses,
                                                                              @Param("month") YearMonth month, @Param("lastId") Long lastId, Pageable pageable);

    @Query("""
            select distinct s.courseEnrollment.id from PaymentPlanSelection s
            where s.courseEnrollment is not null
              and s.paymentPlanOption.cadence = :cadence
              and s.courseEnrollment.status = :status
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
              and s.courseEnrollment.id > :lastId
            order by s.courseEnrollment.id
            """)
    List<Long> findCourseEnrollmentIdsWithActivePlan(@Param("cadence") PaymentPlanCadence cadence,
                                                      @Param("status") EnrollmentStatus status,
                                                      @Param("month") YearMonth month, @Param("lastId") Long lastId, Pageable pageable);

    @Query("""
            select distinct s.programEnrollment.id from PaymentPlanSelection s
            where s.programEnrollment is not null
              and s.programEnrollment.program.id = :programId
              and s.paymentPlanOption.cadence = com.red_escuela.backend.billing.entity.PaymentPlanCadence.MONTHLY
              and s.programEnrollment.status = com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.ENROLLED
              and s.programEnrollment.paymentStatus not in (com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.SUSPENDED, com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT)
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
            """)
    List<Long> findProgramEnrollmentIdsPayingMonthlyInstallments(@Param("programId") Long programId, @Param("month") YearMonth month);

    @Query("""
            select s.programEnrollment.id, s.effectiveFrom, s.scopeMonths from PaymentPlanSelection s
            where s.programEnrollment is not null
              and s.programEnrollment.program.id = :programId
              and s.paymentPlanOption.cadence = com.red_escuela.backend.billing.entity.PaymentPlanCadence.ANNUAL_UPFRONT
              and s.programEnrollment.status = com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.ENROLLED
              and s.programEnrollment.paymentStatus not in (com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.SUSPENDED, com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT)
              and s.scopeMonths is not null
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
            """)
    List<Object[]> findAnnualUpfrontSelectionsOfProgram(@Param("programId") Long programId, @Param("month") YearMonth month);

    @Query("""
            select distinct s.courseEnrollment.id from PaymentPlanSelection s
            where s.courseEnrollment is not null
              and s.courseEnrollment.course.id = :courseId
              and s.paymentPlanOption.cadence = com.red_escuela.backend.billing.entity.PaymentPlanCadence.MONTHLY
              and s.courseEnrollment.status = com.red_escuela.backend.enrollment.entity.EnrollmentStatus.ENROLLED
              and s.courseEnrollment.paymentStatus not in (com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.SUSPENDED, com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT)
              and s.effectiveFrom <= :month
              and (s.effectiveTo is null or s.effectiveTo >= :month)
            """)
    List<Long> findCourseEnrollmentIdsPayingMonthlyInstallments(@Param("courseId") Long courseId, @Param("month") YearMonth month);
}

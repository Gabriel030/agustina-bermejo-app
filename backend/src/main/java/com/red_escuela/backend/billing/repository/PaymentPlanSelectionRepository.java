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
            select pe.id from ProgramEnrollment pe
            where pe.status in :statuses
              and pe.id > :lastId
              and exists (select 1 from PaymentPlanSelection s where s.programEnrollment = pe)
              and not exists (select 1 from PaymentPlanSelection m
                              where m.programEnrollment = pe
                                and m.paymentPlanOption.cadence = :cadence
                                and m.effectiveFrom <= :month
                                and (m.effectiveTo is null or m.effectiveTo >= :month))
            order by pe.id
            """)
    List<Long> findProgramEnrollmentIdsWithoutActivePlan(@Param("cadence") PaymentPlanCadence cadence,
                                                          @Param("statuses") List<ProgramEnrollmentStatus> statuses,
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
            select s from PaymentPlanSelection s join s.paymentPlanOption o
            where o.cadence = com.red_escuela.backend.billing.entity.PaymentPlanCadence.UPFRONT
              and s.programEnrollment is not null
              and s.effectiveTo is null
              and not exists (
                  select i.id from Invoice i
                  where i.paymentPlanSelection = s and i.feeType = com.red_escuela.backend.billing.entity.FeeType.RECURRING_FEE)
            order by s.id
            """)
    List<PaymentPlanSelection> findUpfrontProgramSelectionsWithoutInstallments();
}

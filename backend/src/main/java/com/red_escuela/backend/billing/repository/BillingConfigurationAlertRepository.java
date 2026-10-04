package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.BillingConfigurationAlert;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface BillingConfigurationAlertRepository extends JpaRepository<BillingConfigurationAlert, Long> {

    Optional<BillingConfigurationAlert> findByProgramIdAndPeriod(Long programId, YearMonth period);

    @Query("select a from BillingConfigurationAlert a join fetch a.program where a.resolvedAt is null order by a.period desc")
    List<BillingConfigurationAlert> findByResolvedAtIsNullOrderByPeriodDesc();

    List<BillingConfigurationAlert> findByProgramIdAndResolvedAtIsNull(Long programId);

    interface ProgramCount {
        Long getProgramId();

        Long getTotal();
    }

    @Query("""
            select pe.program.id as programId, count(pe) as total from ProgramEnrollment pe
            where pe.status in :statuses
              and exists (select 1 from PaymentPlanSelection s where s.programEnrollment = pe)
            group by pe.program.id
            """)
    List<ProgramCount> countBillableEnrollmentsByProgram(@Param("statuses") List<ProgramEnrollmentStatus> statuses);
}

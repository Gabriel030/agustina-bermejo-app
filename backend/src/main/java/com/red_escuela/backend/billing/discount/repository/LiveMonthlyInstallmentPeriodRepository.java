package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;

public interface LiveMonthlyInstallmentPeriodRepository extends JpaRepository<InvoiceLine, Long> {

    @Query("""
            select l.period from InvoiceLine l
            where l.programEnrollmentId = :enrollmentId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT
              and l.chargeKeyReleasedAt is null
            """)
    List<YearMonth> findLivePeriodsOfProgramEnrollment(@Param("enrollmentId") Long enrollmentId);

    @Query("""
            select l.period from InvoiceLine l
            where l.courseEnrollmentId = :enrollmentId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT
              and l.chargeKeyReleasedAt is null
            """)
    List<YearMonth> findLivePeriodsOfCourseEnrollment(@Param("enrollmentId") Long enrollmentId);
}

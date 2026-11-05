package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;

public interface LiveEnrollmentFeeRepository extends JpaRepository<InvoiceLine, Long> {

    @Query("""
            select l.period from InvoiceLine l
            where l.programEnrollmentId = :enrollmentId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE
              and l.chargeKeyReleasedAt is null
            """)
    List<YearMonth> findLivePeriodsOfProgramEnrollment(@Param("enrollmentId") Long enrollmentId);

    @Query("""
            select count(l) from InvoiceLine l
            where l.courseEnrollmentId = :enrollmentId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE
              and l.chargeKeyReleasedAt is null
            """)
    long countLiveOfCourseEnrollment(@Param("enrollmentId") Long enrollmentId);

    @Query("""
            select l.period from InvoiceLine l
            where l.programEnrollmentId = :enrollmentId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE
              and l.chargeKeyReleasedAt is null
              and l.invoice.id <> :excludedInvoiceId
            """)
    List<YearMonth> findLivePeriodsOfProgramEnrollmentExcludingInvoice(@Param("enrollmentId") Long enrollmentId, @Param("excludedInvoiceId") Long excludedInvoiceId);

    @Query("""
            select count(l) from InvoiceLine l
            where l.courseEnrollmentId = :enrollmentId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE
              and l.chargeKeyReleasedAt is null
              and l.invoice.id <> :excludedInvoiceId
            """)
    long countLiveOfCourseEnrollmentExcludingInvoice(@Param("enrollmentId") Long enrollmentId, @Param("excludedInvoiceId") Long excludedInvoiceId);
}

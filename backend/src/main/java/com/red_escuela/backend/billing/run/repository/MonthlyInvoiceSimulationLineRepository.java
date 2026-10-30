package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyInvoiceSimulationLineRepository extends JpaRepository<MonthlyInvoiceSimulationLine, Long> {

    List<MonthlyInvoiceSimulationLine> findByMonthlyInvoiceRunIdOrderByIdAsc(Long monthlyInvoiceRunId);

    @Query(value = """
            select 'PROGRAM', pe.program_id, il.amount_currency, sum(il.amount - il.discount_amount)
            from invoice_line il
            join invoice i on i.id = il.invoice_id
            join program_enrollment pe on pe.id = il.program_enrollment_id
            where il.fee_type = 'MONTHLY_INSTALLMENT' and il.period = :period and il.charge_key_released_at is null and i.document_status = 'ISSUED'
            group by pe.program_id, il.amount_currency
            union all
            select 'COURSE', ce.course_id, il.amount_currency, sum(il.amount - il.discount_amount)
            from invoice_line il
            join invoice i on i.id = il.invoice_id
            join course_enrollment ce on ce.id = il.course_enrollment_id
            where il.fee_type = 'MONTHLY_INSTALLMENT' and il.period = :period and il.charge_key_released_at is null and i.document_status = 'ISSUED'
            group by ce.course_id, il.amount_currency
            """, nativeQuery = true)
    List<Object[]> sumIssuedInstallmentsByOffering(@Param("period") LocalDate period);

    @Query(value = "select program_id from program_enrollment where id = :id", nativeQuery = true)
    Long findProgramIdOfProgramEnrollment(@Param("id") Long id);

    @Query(value = "select course_id from course_enrollment where id = :id", nativeQuery = true)
    Long findCourseIdOfCourseEnrollment(@Param("id") Long id);
}

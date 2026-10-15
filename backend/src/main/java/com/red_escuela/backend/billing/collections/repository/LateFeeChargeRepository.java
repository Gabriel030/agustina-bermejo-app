package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LateFeeChargeRepository extends Repository<LateFeeCharge, Long> {

    LateFeeCharge save(LateFeeCharge charge);

    @Query("select new com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod(c.periodStart, c.periodEnd) from LateFeeCharge c "
            + "where c.originInvoiceId = :originInvoiceId order by c.periodStart")
    List<LateFeeAccrualPeriod> findCoveredPeriodsByOriginInvoiceId(@Param("originInvoiceId") Long originInvoiceId);

    @Query(value = """
            select i.id from invoice i
            join payment_allocation a on a.invoice_id = i.id and a.reverses_allocation_id is null
              and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = a.id)
            where i.payment_status = 'PAID' and i.document_type = 'INVOICE' and i.invoice_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
              and i.id > :lastId and a.allocated_on >= :since
              and a.effective_date > i.due_date + coalesce(
                    (select (v.config_values ->> 'gracePeriodDays')::int from configuration_version v
                     where v.scope = 'LATE_PAYMENT' and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED') and v.effective_from <= i.due_date
                     order by v.effective_from desc, v.decided_at desc, v.id desc limit 1),
                    (select (v.config_values ->> 'gracePeriodDays')::int from configuration_version v
                     where v.scope = 'LATE_PAYMENT' and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')
                     order by v.effective_from asc, v.decided_at asc, v.id asc limit 1), 0)
              and (not exists (select 1 from late_fee_charge c where c.origin_invoice_id = i.id and c.period_end >= a.effective_date)
                   or (exists (select 1 from late_fee_charge c where c.origin_invoice_id = i.id and c.period_end > a.effective_date)
                       and not exists (select 1 from processed_effect p where p.handler_name = 'LateFeeOverchargeRecorder'
                                       and p.event_id = lpad(to_hex(a.id), 32, '0')::uuid))) 
            order by i.id asc
            limit :pageSize
            """, nativeQuery = true)
    List<Long> findRecentlySettledLateFeeOriginIdsAfter(@Param("lastId") Long lastId, @Param("since") LocalDate since, @Param("pageSize") int pageSize);

    List<LateFeeCharge> findByOriginInvoiceIdOrderByPeriodStart(Long originInvoiceId);

    Optional<LateFeeCharge> findByDebitNoteId(Long debitNoteId);

    Optional<LateFeeCharge> findById(Long id);

    @Query(value = """
            select c.id from late_fee_charge c
            where (select l.program_enrollment_id from invoice_line l where l.invoice_id = c.origin_invoice_id order by l.id limit 1) = :programEnrollmentId
            order by c.id
            """, nativeQuery = true)
    List<Long> findChargeIdsOfProgramEnrollment(@Param("programEnrollmentId") Long programEnrollmentId);

    @Query(value = """
            select c.id from late_fee_charge c
            where (select l.course_enrollment_id from invoice_line l where l.invoice_id = c.origin_invoice_id order by l.id limit 1) = :courseEnrollmentId
            order by c.id
            """, nativeQuery = true)
    List<Long> findChargeIdsOfCourseEnrollment(@Param("courseEnrollmentId") Long courseEnrollmentId);
}

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
            join (
                select a.invoice_id as invoice_id, a.effective_date as settled_on, lpad(to_hex(a.id), 32, '0')::uuid as marker
                from payment_allocation a
                where a.reverses_allocation_id is null and a.allocated_on >= :since
                  and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = a.id)
                union all
                select n.invoice_id, n.issue_date, (lpad('1', 16, '0') || lpad(to_hex(n.id), 16, '0'))::uuid
                from credit_note n
                where n.issue_date >= :since and n.reason <> 'CANCELLATION' and n.offset_amount > 0
                  and n.id = (select max(m.id) from credit_note m where m.invoice_id = n.invoice_id and m.reason <> 'CANCELLATION' and m.offset_amount > 0)
                  and not exists (select 1 from payment_allocation c where c.invoice_id = n.invoice_id and c.reverses_allocation_id is null
                                  and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = c.id))
            ) s on s.invoice_id = i.id
            where i.payment_status = 'PAID' and i.document_type = 'INVOICE' and i.invoice_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
              and i.id > :lastId
              and s.settled_on > i.due_date + coalesce(
                    (select (v.config_values ->> 'gracePeriodDays')::int from configuration_version v
                     where v.scope = 'LATE_PAYMENT' and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED') and v.effective_from <= i.due_date
                     order by v.effective_from desc, v.decided_at desc, v.id desc limit 1),
                    (select (v.config_values ->> 'gracePeriodDays')::int from configuration_version v
                     where v.scope = 'LATE_PAYMENT' and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')
                     order by v.effective_from asc, v.decided_at asc, v.id asc limit 1), 0)
              and (not exists (select 1 from late_fee_charge c where c.origin_invoice_id = i.id and c.period_end >= s.settled_on)
                   or (exists (select 1 from late_fee_charge c where c.origin_invoice_id = i.id and c.period_end > s.settled_on)
                       and not exists (select 1 from processed_effect p where p.handler_name = 'LateFeeOverchargeRecorder' and p.event_id = s.marker)))
            order by i.id asc
            limit :pageSize
            """, nativeQuery = true)
    List<Long> findRecentlySettledLateFeeOriginIdsAfter(@Param("lastId") Long lastId, @Param("since") LocalDate since, @Param("pageSize") int pageSize);

    List<LateFeeCharge> findByOriginInvoiceIdOrderByPeriodStart(Long originInvoiceId);

    Optional<LateFeeCharge> findByDebitNoteId(Long debitNoteId);

    Optional<LateFeeCharge> findById(Long id);

    @Query(value = """
            select c.id from late_fee_charge c
            join invoice d on d.id = c.debit_note_id
            join lateral (
                select coalesce(pe.completion_date, ce.completion_date)::date as end_date
                from invoice_line l
                left join program_enrollment pe on pe.id = l.program_enrollment_id and pe.status in ('COMPLETED', 'WITHDRAWN')
                left join course_enrollment ce on ce.id = l.course_enrollment_id and ce.status in ('WITHDRAWN', 'APPROVED', 'FAILED', 'WAITLIST_EXPIRED')
                where l.invoice_id = c.origin_invoice_id order by l.id limit 1) e on e.end_date is not null
            where c.id > :lastId and d.document_status = 'ISSUED' and d.payment_status not in ('CANCELLED', 'PAYMENT_UNDER_REVIEW')
              and exists (select 1 from invoice_line nl where nl.invoice_id = d.id
                          and (nl.amount - nl.discount_amount) - nl.credited_amount - nl.free_credited_amount > 0)
              and c.charged_days - coalesce((select sum(k.corrected_days) from late_fee_charge_correction k where k.late_fee_charge_id = c.id), 0)
                  > greatest(0, least(c.period_end, e.end_date) - c.period_start + 1)
            order by c.id
            limit :pageSize
            """, nativeQuery = true)
    List<Long> findChargeIdsWithDaysAfterTheEnrollmentEndAfter(@Param("lastId") Long lastId, @Param("pageSize") int pageSize);

    @Query(value = """
            select to_char(coalesce(pe.withdrawal_registered_on, ce.withdrawal_registered_on), 'YYYY-MM-DD')
            from invoice_line l
            left join program_enrollment pe on pe.id = l.program_enrollment_id and pe.status in ('COMPLETED', 'WITHDRAWN')
            left join course_enrollment ce on ce.id = l.course_enrollment_id and ce.status in ('WITHDRAWN', 'APPROVED', 'FAILED', 'WAITLIST_EXPIRED')
            where l.invoice_id = (select c.origin_invoice_id from late_fee_charge c where c.id = :chargeId) order by l.id limit 1
            """, nativeQuery = true)
    Optional<String> findEnrollmentEndRegistrationDateOfCharge(@Param("chargeId") Long chargeId);

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

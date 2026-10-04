package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCourseEnrollmentIdOrderByPeriod(Long courseEnrollmentId);

    List<Invoice> findByProgramEnrollmentIdOrderByPeriod(Long programEnrollmentId);

    boolean existsByProgramEnrollmentIdAndFeeTypeAndPeriod(Long programEnrollmentId, FeeType feeType, YearMonth period);

    boolean existsByCourseEnrollmentIdAndFeeTypeAndPeriod(Long courseEnrollmentId, FeeType feeType, YearMonth period);

    long countByProgramEnrollmentIdAndFeeTypeAndPeriodBetween(Long programEnrollmentId, FeeType feeType, YearMonth from, YearMonth to);

    Optional<Invoice> findFirstByProgramEnrollmentIdAndFeeTypeOrderByPeriodDesc(Long programEnrollmentId, FeeType feeType);

    @Query("select distinct i from Invoice i left join fetch i.adjustments where i.programEnrollment.id = :programEnrollmentId order by i.period")
    List<Invoice> findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(@Param("programEnrollmentId") Long programEnrollmentId);

    @Query("""
            select distinct i from Invoice i
            left join fetch i.allocations a
            left join fetch a.payment p
            left join fetch p.registeredBy
            left join fetch i.cancelledBy
            where i.courseEnrollment.id = :courseEnrollmentId
            order by i.period
            """)
    List<Invoice> findByCourseEnrollmentIdWithPayments(@Param("courseEnrollmentId") Long courseEnrollmentId);

    @Query("""
            select distinct i from Invoice i
            left join fetch i.allocations a
            left join fetch a.payment p
            left join fetch p.registeredBy
            left join fetch i.cancelledBy
            where i.programEnrollment.id = :programEnrollmentId
            order by i.period
            """)
    List<Invoice> findByProgramEnrollmentIdWithPayments(@Param("programEnrollmentId") Long programEnrollmentId);

    @Query("""
            select distinct i from Invoice i
            left join fetch i.adjustments adj
            left join fetch adj.appliedBy
            where i.id in :ids
            """)
    List<Invoice> fetchAdjustments(@Param("ids") Collection<Long> ids);

    @Query("select i from Invoice i left join fetch i.adjustments where i.id = :id")
    Optional<Invoice> findByIdWithAdjustments(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    List<Invoice> findByStatusAndDueDateBeforeAndIdGreaterThanOrderByIdAsc(
            InvoiceStatus status, LocalDate dueDate, Long lastId, Pageable pageable);

    Optional<Invoice> findFirstByCourseEnrollmentIdAndStatusInOrderByDueDateAsc(Long courseEnrollmentId, List<InvoiceStatus> statuses);

    Optional<Invoice> findFirstByProgramEnrollmentIdAndStatusInOrderByDueDateAsc(Long programEnrollmentId, List<InvoiceStatus> statuses);

    long countByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, InvoiceStatus status);

    long countByProgramEnrollmentIdAndStatus(Long programEnrollmentId, InvoiceStatus status);

    @Query("""
            select case when count(i) > 0 then true else false end from Invoice i
            left join i.courseEnrollment ce
            left join i.programEnrollment pe
            where i.status = :status
              and (ce.student.id = :studentId or pe.student.id = :studentId)
            """)
    boolean existsByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") InvoiceStatus status);

    List<Invoice> findByCourseEnrollmentIdAndPeriodBetween(Long courseEnrollmentId, YearMonth from, YearMonth to);

    List<Invoice> findByProgramEnrollmentIdAndPeriodBetween(Long programEnrollmentId, YearMonth from, YearMonth to);

    List<Invoice> findByCourseEnrollmentIdAndStatusIn(Long courseEnrollmentId, List<InvoiceStatus> statuses);

    List<Invoice> findByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<InvoiceStatus> statuses);

    @Query("""
            select count(i) from Invoice i join i.adjustments a
            where i.paymentPlanSelection.id = :paymentPlanSelectionId
              and i.feeType = :feeType
              and a.promotionBenefit.id = :promotionBenefitId
            """)
    long countByPaymentPlanSelectionAndFeeTypeAndPromotionBenefit(
            @Param("paymentPlanSelectionId") Long paymentPlanSelectionId, @Param("feeType") FeeType feeType, @Param("promotionBenefitId") Long promotionBenefitId);

    @Query("""
            select i.paymentPlanSelection.id as paymentPlanSelectionId, i.feeType as feeType,
                   a.promotionBenefit.id as promotionBenefitId, count(i) as used
            from Invoice i join i.adjustments a
            where i.paymentPlanSelection.id in :paymentPlanSelectionIds
              and a.promotionBenefit is not null
            group by i.paymentPlanSelection.id, i.feeType, a.promotionBenefit.id
            """)
    List<PromotionBenefitUsage> countPromotionBenefitUsageBySelections(
            @Param("paymentPlanSelectionIds") List<Long> paymentPlanSelectionIds);

    @Query("select distinct ce.student.id from Invoice i join i.courseEnrollment ce where i.status = :status")
    List<Long> findCourseStudentIdsByStatus(@Param("status") InvoiceStatus status);

    @Query("select distinct pe.student.id from Invoice i join i.programEnrollment pe where i.status = :status")
    List<Long> findProgramStudentIdsByStatus(@Param("status") InvoiceStatus status);

    @Query("""
            select i from Invoice i
            join fetch i.courseEnrollment ce
            join fetch ce.student s
            join fetch s.person
            join fetch ce.course
            where i.status in :statuses
            """)
    List<Invoice> findByCourseEnrollmentStatusInWithStudent(@Param("statuses") Collection<InvoiceStatus> statuses);

    @Query("""
            select i from Invoice i
            join fetch i.programEnrollment pe
            join fetch pe.student s
            join fetch s.person
            join fetch pe.program
            where i.status in :statuses
            """)
    List<Invoice> findByProgramEnrollmentStatusInWithStudent(@Param("statuses") Collection<InvoiceStatus> statuses);

    interface CurrencyTotal {
        String getCurrency();

        BigDecimal getTotal();
    }

    @Query(value = """
            select i.base_amount_currency as currency,
                   coalesce(sum(greatest(greatest(i.base_amount + coalesce(adj.total, 0), 0) - coalesce(paid.total, 0), 0)), 0) as total
            from invoice i
            left join (
                select a.invoice_id,
                       sum(case when a.type = 'SURCHARGE' then 1 else -1 end
                           * case a.value ->> 'type'
                                 when 'NOMINAL' then cast(a.value ->> 'amount' as numeric)
                                 else round(a.resolved_base * cast(a.value ->> 'fraction' as numeric), 2)
                             end) as total
                from invoice_adjustment a
                group by a.invoice_id) adj on adj.invoice_id = i.id
            left join (
                select p.invoice_id, sum(p.amount_applied) as total
                from payment_allocation p
                group by p.invoice_id) paid on paid.invoice_id = i.id
            where i.status = :status
            group by i.base_amount_currency
            """, nativeQuery = true)
    List<CurrencyTotal> sumOutstandingBalanceByStatus(@Param("status") String status);

    @Query(value = """
            select i.base_amount_currency as currency,
                   coalesce(sum(greatest(i.base_amount + coalesce(adj.total, 0), 0)), 0) as total
            from invoice i
            left join (
                select a.invoice_id,
                       sum(case when a.type = 'SURCHARGE' then 1 else -1 end
                           * case a.value ->> 'type'
                                 when 'NOMINAL' then cast(a.value ->> 'amount' as numeric)
                                 else round(a.resolved_base * cast(a.value ->> 'fraction' as numeric), 2)
                             end) as total
                from invoice_adjustment a
                group by a.invoice_id) adj on adj.invoice_id = i.id
            where i.period = :period and i.status <> 'CANCELLED'
            group by i.base_amount_currency
            """, nativeQuery = true)
    List<CurrencyTotal> sumAmountDueByPeriod(@Param("period") LocalDate period);

    @Query(value = """
            select i.base_amount_currency as currency, coalesce(sum(alloc.amount_applied), 0) as total
            from payment_allocation alloc
            join invoice i on i.id = alloc.invoice_id
            where i.period = :period and i.status <> 'CANCELLED'
            group by i.base_amount_currency
            """, nativeQuery = true)
    List<CurrencyTotal> sumAllocatedByPeriod(@Param("period") LocalDate period);
}

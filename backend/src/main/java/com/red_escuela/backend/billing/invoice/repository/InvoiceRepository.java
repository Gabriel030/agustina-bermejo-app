package com.red_escuela.backend.billing.invoice.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends Repository<Invoice, Long> {

    Invoice save(Invoice invoice);

    Invoice saveAndFlush(Invoice invoice);

    void flush();

    long count();

    List<Invoice> findAll();

    Optional<Invoice> findById(Long id);

    @Query("select distinct i from Invoice i left join fetch i.lines where i.id = :id")
    Optional<Invoice> findInvoiceWithLinesById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    @Query("select l.invoice.id from InvoiceLine l where l.chargeKey = :chargeKey and l.chargeKeyReleasedAt is null")
    Optional<Long> findLiveInvoiceIdByChargeKey(@Param("chargeKey") String chargeKey);

    @Query("""
            select count(d) from InvoiceDiscount d, StudentCurrentAccount a
            where d.discountType = com.red_escuela.backend.billing.invoice.domain.DiscountType.PROMOTION
              and d.studentDiscountId = :benefitId
              and a.id = d.invoiceLine.invoice.studentCurrentAccountId
              and a.studentId = :studentId
              and d.invoiceLine.invoice.documentStatus <> com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DISCARDED
            """)
    long countPromotionDiscountsOfStudent(@Param("benefitId") Long benefitId, @Param("studentId") Long studentId);

    @Query("select distinct i from Invoice i join fetch i.lines l where l.programEnrollmentId = :enrollmentId")
    List<Invoice> findByProgramEnrollmentId(@Param("enrollmentId") Long enrollmentId);

    @Query("select distinct i from Invoice i join fetch i.lines l where l.courseEnrollmentId = :enrollmentId")
    List<Invoice> findByCourseEnrollmentId(@Param("enrollmentId") Long enrollmentId);

    default List<Invoice> findByProgramEnrollmentIdOrderByPeriod(Long enrollmentId) {
        return findByProgramEnrollmentId(enrollmentId).stream().sorted(PERIOD_ORDER).toList();
    }

    default List<Invoice> findByCourseEnrollmentIdOrderByPeriod(Long enrollmentId) {
        return findByCourseEnrollmentId(enrollmentId).stream().sorted(PERIOD_ORDER).toList();
    }

    Comparator<Invoice> PERIOD_ORDER = Comparator.comparing((Invoice i) -> i.getPrimaryLine().getPeriod()).thenComparing(Invoice::getId);

    default List<Invoice> findByProgramEnrollmentIdAndPaymentStatusIn(Long enrollmentId, Collection<InvoicePaymentStatus> statuses) {
        return findByProgramEnrollmentId(enrollmentId).stream().filter(i -> statuses.contains(i.getPaymentStatus())).toList();
    }

    default List<Invoice> findByCourseEnrollmentIdAndPaymentStatusIn(Long enrollmentId, Collection<InvoicePaymentStatus> statuses) {
        return findByCourseEnrollmentId(enrollmentId).stream().filter(i -> statuses.contains(i.getPaymentStatus())).toList();
    }

    default Optional<Invoice> findFirstByProgramEnrollmentIdAndPaymentStatusInOrderByDueDateAsc(Long enrollmentId, Collection<InvoicePaymentStatus> statuses) {
        return findByProgramEnrollmentIdAndPaymentStatusIn(enrollmentId, statuses).stream().min(Comparator.comparing(Invoice::getDueDate));
    }

    default Optional<Invoice> findFirstByCourseEnrollmentIdAndPaymentStatusInOrderByDueDateAsc(Long enrollmentId, Collection<InvoicePaymentStatus> statuses) {
        return findByCourseEnrollmentIdAndPaymentStatusIn(enrollmentId, statuses).stream().min(Comparator.comparing(Invoice::getDueDate));
    }

    default Optional<Invoice> findFirstByProgramEnrollmentIdAndFeeTypeAndPeriod(Long enrollmentId, FeeType feeType, YearMonth period) {
        return findByProgramEnrollmentId(enrollmentId).stream()
                .filter(i -> i.getPrimaryLine().getFeeType() == feeType && i.getPrimaryLine().getPeriod().equals(period)).findFirst();
    }

    default Optional<Invoice> findFirstByCourseEnrollmentIdAndFeeTypeAndPeriod(Long enrollmentId, FeeType feeType, YearMonth period) {
        return findByCourseEnrollmentId(enrollmentId).stream()
                .filter(i -> i.getPrimaryLine().getFeeType() == feeType && i.getPrimaryLine().getPeriod().equals(period)).findFirst();
    }

    default Optional<Invoice> findFirstByProgramEnrollmentIdAndFeeTypeAndTemplateIdAndEnrollmentFeeSlot(Long enrollmentId, FeeType feeType, Long templateId,
                                                                                                        Integer slot) {
        return findByProgramEnrollmentId(enrollmentId).stream().filter(i -> i.getPrimaryLine().getFeeType() == feeType
                && java.util.Objects.equals(i.getPrimaryLine().getTemplateId(), templateId)
                && java.util.Objects.equals(i.getPrimaryLine().getEnrollmentFeeSlot(), slot)).findFirst();
    }

    default boolean existsByProgramEnrollmentIdAndFeeTypeAndPeriod(Long enrollmentId, FeeType feeType, YearMonth period) {
        return findFirstByProgramEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, feeType, period).isPresent();
    }

    default boolean existsByCourseEnrollmentIdAndFeeTypeAndPeriod(Long enrollmentId, FeeType feeType, YearMonth period) {
        return findFirstByCourseEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, feeType, period).isPresent();
    }

    @Query("""
            select distinct l.enrollmentFeeSlot from InvoiceLine l
            where l.templateId = :templateId and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE
              and l.enrollmentFeeSlot is not null and l.chargeKeyReleasedAt is null
            """)
    List<Integer> findIssuedEnrollmentFeeSlotsByTemplateId(@Param("templateId") Long templateId);

    @Query("""
            select distinct l.period from InvoiceLine l, ProgramEnrollment pe
            where pe.id = l.programEnrollmentId and pe.program.id = :programId
              and l.feeType = com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT
              and l.chargeKeyReleasedAt is null
              and l.period between :from and :to
            """)
    List<YearMonth> findIssuedInstallmentPeriodsByProgramIdAndPeriodBetween(@Param("programId") Long programId, @Param("from") YearMonth from,
                                                                            @Param("to") YearMonth to);

    @Query("""
            select i from Invoice i
            where i.paymentStatus = :status and i.dueDate < :dueDate and i.id > :lastId
            order by i.id asc
            """)
    List<Invoice> findByPaymentStatusAndDueDateBeforeAndIdGreaterThanOrderByIdAsc(@Param("status") InvoicePaymentStatus status,
                                                                                 @Param("dueDate") LocalDate dueDate, @Param("lastId") Long lastId,
                                                                                 Pageable pageable);

    @Query("select count(distinct i) from Invoice i join i.lines l where l.programEnrollmentId = :enrollmentId and i.paymentStatus = :status")
    long countByProgramEnrollmentIdAndPaymentStatus(@Param("enrollmentId") Long enrollmentId, @Param("status") InvoicePaymentStatus status);

    @Query("select count(distinct i) from Invoice i join i.lines l where l.courseEnrollmentId = :enrollmentId and i.paymentStatus = :status")
    long countByCourseEnrollmentIdAndPaymentStatus(@Param("enrollmentId") Long enrollmentId, @Param("status") InvoicePaymentStatus status);

    @Query("""
            select count(i) > 0 from Invoice i, StudentCurrentAccount a
            where a.id = i.studentCurrentAccountId and a.studentId = :studentId and i.paymentStatus = :status
            """)
    boolean existsByStudentIdAndPaymentStatus(@Param("studentId") Long studentId, @Param("status") InvoicePaymentStatus status);

    @Query("select a.studentId from StudentCurrentAccount a, Invoice i where i.id = :invoiceId and a.id = i.studentCurrentAccountId")
    Optional<Long> findOwnerStudentIdByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
            select distinct a.studentId from Invoice i, StudentCurrentAccount a
            where a.id = i.studentCurrentAccountId and i.paymentStatus = :status
            """)
    List<Long> findStudentIdsByPaymentStatus(@Param("status") InvoicePaymentStatus status);

    @Query("select distinct i from Invoice i join fetch i.lines where i.paymentStatus = :status")
    List<Invoice> findByPaymentStatusWithLines(@Param("status") InvoicePaymentStatus status);

    interface CurrencyTotal {
        String getCurrency();

        BigDecimal getTotal();
    }

    @Query(value = """
            select i.currency as currency, coalesce(sum(i.outstanding_amount), 0) as total
            from invoice i where i.payment_status = :status group by i.currency
            """, nativeQuery = true)
    List<CurrencyTotal> sumOutstandingAmountByPaymentStatus(@Param("status") String status);

    @Query(value = """
            select i.currency as currency, coalesce(sum(i.total), 0) as total
            from invoice i join invoice_line l on l.invoice_id = i.id and l.line_number = 1
            where l.period = :period and i.document_status = 'ISSUED' and i.payment_status <> 'CANCELLED'
            group by i.currency
            """, nativeQuery = true)
    List<CurrencyTotal> sumTotalByPeriod(@Param("period") LocalDate period);

    @Query(value = """
            select i.currency as currency, coalesce(sum(i.paid_amount), 0) as total
            from invoice i join invoice_line l on l.invoice_id = i.id and l.line_number = 1
            where l.period = :period and i.document_status = 'ISSUED' and i.payment_status <> 'CANCELLED'
            group by i.currency
            """, nativeQuery = true)
    List<CurrencyTotal> sumPaidByPeriod(@Param("period") LocalDate period);
}

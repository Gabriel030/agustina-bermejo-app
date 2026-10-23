package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.discount.domain.LiveInvoiceDiscount;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AppliedStudentDiscountRepository extends JpaRepository<InvoiceDiscount, Long> {

    @Query("""
            select new com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment(d.studentDiscountId, l.period, d.installmentNumber, d.installmentsTotal)
            from InvoiceDiscount d join d.invoiceLine l
            where d.studentDiscountId in :studentDiscountIds
              and d.consumptionReleasedAt is null
              and l.chargeKeyReleasedAt is null
            order by l.period, d.id
            """)
    List<AppliedStudentDiscountInstallment> findLiveApplicationsOf(@Param("studentDiscountIds") Collection<Long> studentDiscountIds);

    @Query("""
            select new com.red_escuela.backend.billing.discount.domain.LiveInvoiceDiscount(d.id, d.studentDiscountId)
            from InvoiceDiscount d join d.invoiceLine l
            where l.invoice.id = :invoiceId
              and d.consumptionReleasedAt is null
            order by d.id
            """)
    List<LiveInvoiceDiscount> findLiveDiscountsOfInvoice(@Param("invoiceId") Long invoiceId);

    @Modifying(flushAutomatically = true)
    @Query(value = "update invoice_discount set consumption_released_at = :releasedAt where id in (:invoiceDiscountIds) and consumption_released_at is null", nativeQuery = true)
    int markConsumptionReleased(@Param("invoiceDiscountIds") Collection<Long> invoiceDiscountIds, @Param("releasedAt") LocalDateTime releasedAt);
}

package com.red_escuela.backend.billing.invoice.repository;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceRepository extends Repository<Invoice, Long> {

    Invoice save(Invoice invoice);

    void flush();

    Optional<Invoice> findById(Long id);

    @Query("select distinct i from Invoice i left join fetch i.lines where i.id = :id")
    Optional<Invoice> findInvoiceWithLinesById(@Param("id") Long id);

    @Query("select l.invoice.id from InvoiceLine l where l.chargeKey = :chargeKey and l.chargeKeyReleasedAt is null")
    Optional<Long> findLiveInvoiceIdByChargeKey(@Param("chargeKey") String chargeKey);

    @Query("""
            select count(d) from InvoiceDiscount d
            where d.discountType = com.red_escuela.backend.billing.invoice.domain.DiscountType.PROMOTION
              and d.studentDiscountId = :benefitId
              and d.invoiceLine.programEnrollmentId = :programEnrollmentId
              and d.invoiceLine.invoice.documentStatus <> com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DISCARDED
            """)
    long countPromotionDiscountsOfProgramEnrollment(@Param("benefitId") Long benefitId, @Param("programEnrollmentId") Long programEnrollmentId);

    @Query("""
            select count(d) from InvoiceDiscount d
            where d.discountType = com.red_escuela.backend.billing.invoice.domain.DiscountType.PROMOTION
              and d.studentDiscountId = :benefitId
              and d.invoiceLine.courseEnrollmentId = :courseEnrollmentId
              and d.invoiceLine.invoice.documentStatus <> com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DISCARDED
            """)
    long countPromotionDiscountsOfCourseEnrollment(@Param("benefitId") Long benefitId, @Param("courseEnrollmentId") Long courseEnrollmentId);
}

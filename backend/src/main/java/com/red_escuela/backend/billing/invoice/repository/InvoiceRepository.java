package com.red_escuela.backend.billing.invoice.repository;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
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
            select count(d) from InvoiceDiscount d, StudentCurrentAccount a
            where d.discountType = com.red_escuela.backend.billing.invoice.domain.DiscountType.PROMOTION
              and d.studentDiscountId = :benefitId
              and a.id = d.invoiceLine.invoice.studentCurrentAccountId
              and a.studentId = :studentId
              and d.invoiceLine.invoice.documentStatus <> com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DISCARDED
            """)
    long countPromotionDiscountsOfStudent(@Param("benefitId") Long benefitId, @Param("studentId") Long studentId);
}

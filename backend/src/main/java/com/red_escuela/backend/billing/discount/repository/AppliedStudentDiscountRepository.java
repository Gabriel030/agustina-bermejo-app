package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

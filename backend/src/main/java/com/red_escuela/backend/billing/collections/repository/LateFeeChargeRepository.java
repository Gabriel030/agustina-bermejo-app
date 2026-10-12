package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LateFeeChargeRepository extends Repository<LateFeeCharge, Long> {

    LateFeeCharge save(LateFeeCharge charge);

    @Query("select new com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod(c.periodStart, c.periodEnd) from LateFeeCharge c "
            + "where c.originInvoiceId = :originInvoiceId order by c.periodStart")
    List<LateFeeAccrualPeriod> findCoveredPeriodsByOriginInvoiceId(@Param("originInvoiceId") Long originInvoiceId);

    List<LateFeeCharge> findByOriginInvoiceIdOrderByPeriodStart(Long originInvoiceId);

    Optional<LateFeeCharge> findByDebitNoteId(Long debitNoteId);
}

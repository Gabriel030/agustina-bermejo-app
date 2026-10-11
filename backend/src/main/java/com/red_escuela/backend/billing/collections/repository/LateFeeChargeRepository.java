package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LateFeeChargeRepository extends Repository<LateFeeCharge, Long> {

    LateFeeCharge save(LateFeeCharge charge);

    @Query("select c.monthOrdinal from LateFeeCharge c where c.originInvoiceId = :originInvoiceId")
    Set<Integer> findMonthOrdinalsByOriginInvoiceId(@Param("originInvoiceId") Long originInvoiceId);

    List<LateFeeCharge> findByOriginInvoiceIdOrderByMonthOrdinal(Long originInvoiceId);

    Optional<LateFeeCharge> findByDebitNoteId(Long debitNoteId);
}

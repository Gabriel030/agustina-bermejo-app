package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.collections.domain.LateFeeChargeCorrection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LateFeeChargeCorrectionRepository extends Repository<LateFeeChargeCorrection, Long> {

    LateFeeChargeCorrection save(LateFeeChargeCorrection correction);

    @Query("select k from LateFeeChargeCorrection k, LateFeeCharge c where c.id = k.lateFeeChargeId and c.originInvoiceId = :originInvoiceId "
            + "and k.cause <> com.red_escuela.backend.billing.collections.domain.LateFeeCorrectionCause.BACKDATED_WITHDRAWAL "
            + "and not exists (select 1 from LateFeeCharge r where r.reinstatesCorrectionId = k.id) order by k.id")
    List<LateFeeChargeCorrection> findPaymentCorrectionsNotReinstatedOfOriginInvoice(@Param("originInvoiceId") Long originInvoiceId);

    List<LateFeeChargeCorrection> findByLateFeeChargeIdOrderByFirstCorrectedDay(Long lateFeeChargeId);
}

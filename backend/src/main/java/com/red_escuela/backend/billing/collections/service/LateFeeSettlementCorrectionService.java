package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.InvoiceSettlement;
import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class LateFeeSettlementCorrectionService {
    private static final Logger log = LoggerFactory.getLogger(LateFeeSettlementCorrectionService.class);

    private final LateFeeChargeRepository chargeRepository;
    private final LateFeeCorrectionIssuer issuer;

    public LateFeeSettlementCorrectionService(LateFeeChargeRepository chargeRepository, LateFeeCorrectionIssuer issuer) {
        this.chargeRepository = chargeRepository;
        this.issuer = issuer;
    }

    public int correctLateFeesChargedAfterThePaymentOf(Long originInvoiceId, InvoiceSettlement settlement) {
        Objects.requireNonNull(originInvoiceId, "originInvoiceId");
        Objects.requireNonNull(settlement, "settlement");
        List<LateFeeCharge> charges = chargeRepository.findByOriginInvoiceIdOrderByPeriodStart(originInvoiceId);
        int corrected = 0;
        int failed = 0;
        for (LateFeeCharge charge : charges) {
            try {
                if (issuer.correctLateFeeChargeAfterPaymentDateInNewTransaction(charge.getId(), settlement) == LateFeeCorrectionOutcome.CORRECTED) {
                    corrected++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Falló la anulación del recargo del cargo id={} por la fecha de pago", charge.getId(), e);
            }
        }
        if (corrected > 0) {
            log.info("Recargos anulados por la fecha de pago: origen={}, imputacion={}, fechaDePago={}, anulados={}", originInvoiceId, settlement.allocationId(),
                    settlement.paymentDate(), corrected);
        }
        if (failed > 0) {
            throw new IllegalStateException("The late fee annulment by payment date failed for " + failed + " charges");
        }
        return corrected;
    }
}

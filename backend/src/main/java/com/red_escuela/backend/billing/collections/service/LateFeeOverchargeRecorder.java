package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.InvoiceSettlement;
import com.red_escuela.backend.billing.account.service.InvoiceSettlementQueryService;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.collections.repository.LateFeeOverchargeMarker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class LateFeeOverchargeRecorder {
    private static final Logger log = LoggerFactory.getLogger(LateFeeOverchargeRecorder.class);

    private final InvoiceSettlementQueryService settlementQuery;
    private final LateFeeChargeRepository chargeRepository;
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    public LateFeeOverchargeRecorder(InvoiceSettlementQueryService settlementQuery, LateFeeChargeRepository chargeRepository, MeterRegistry meterRegistry,
                                     JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.settlementQuery = settlementQuery;
        this.chargeRepository = chargeRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public int recordDaysChargedAfterThePaymentOfInvoice(Long originInvoiceId) {
        Optional<InvoiceSettlement> settlement = settlementQuery.findCurrentSettlementOfInvoice(originInvoiceId);
        if (settlement.isEmpty()) {
            return 0;
        }
        List<LateFeeAccrualPeriod> covered = chargeRepository.findCoveredPeriodsByOriginInvoiceId(originInvoiceId);
        int overchargedDays = covered.stream().mapToInt(period -> period.daysAfter(settlement.get().paymentDate())).sum();
        if (overchargedDays > 0 && claimRecordingOf(settlement.get().allocationId())) {
            LateFeeAccrualPeriod lastCovered = covered.get(covered.size() - 1);
            log.warn("Recargo cobrado de más por un pago con fecha anterior a días ya facturados: origen={}, imputacion={}, fechaDePago={}, ultimoDiaCubierto={}, diasDeMas={}",
                    originInvoiceId, settlement.get().allocationId(), settlement.get().paymentDate(), lastCovered.lastDay(), overchargedDays);
            meterRegistry.counter("billing_late_fees_overcharged_days_total").increment(overchargedDays);
        }
        return overchargedDays;
    }

    private boolean claimRecordingOf(Long allocationId) {
        return jdbcTemplate.update("insert into processed_effect (event_id, handler_name, processed_at) values (?, ?, now()) on conflict do nothing",
                LateFeeOverchargeMarker.markerOf(allocationId), LateFeeOverchargeMarker.HANDLER_NAME) == 1;
    }
}

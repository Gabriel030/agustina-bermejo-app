package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.InvoiceSettlement;
import com.red_escuela.backend.billing.account.service.InvoiceSettlementQueryService;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.collections.repository.LateFeeOverchargeMarker;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Component
public class LateFeeOverchargeRecorder {
    private static final Logger log = LoggerFactory.getLogger(LateFeeOverchargeRecorder.class);

    private final InvoiceSettlementQueryService settlementQuery;
    private final LateFeeChargeRepository chargeRepository;
    private final MeterRegistry meterRegistry;
    private final InvoiceCreditAccessService invoiceAccess;
    private final LateFeeContextLoader contextLoader;
    private final JdbcTemplate jdbcTemplate;

    public LateFeeOverchargeRecorder(InvoiceSettlementQueryService settlementQuery, LateFeeChargeRepository chargeRepository, MeterRegistry meterRegistry,
                                     InvoiceCreditAccessService invoiceAccess, LateFeeContextLoader contextLoader, JdbcTemplate jdbcTemplate) {
        this.invoiceAccess = invoiceAccess;
        this.contextLoader = contextLoader;
        this.jdbcTemplate = jdbcTemplate;
        this.settlementQuery = settlementQuery;
        this.chargeRepository = chargeRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public int recordDaysChargedAfterThePaymentOfInvoice(Long originInvoiceId, LatePaymentTimeline timeline) {
        Optional<InvoiceSettlement> settlement = settlementQuery.findCurrentSettlementOfInvoice(originInvoiceId);
        if (settlement.isEmpty()) {
            return 0;
        }
        List<LateFeeAccrualPeriod> covered = chargeRepository.findCoveredPeriodsByOriginInvoiceId(originInvoiceId);
        Optional<Predicate<LocalDate>> chargedOn = invoiceAccess.findInvoice(originInvoiceId).flatMap(origin -> contextLoader.findChargedDayPredicate(origin, timeline));
        if (chargedOn.isEmpty()) {
            return 0;
        }
        int overchargedDays = covered.stream().mapToInt(period -> period.chargedDaysAfter(settlement.get().paymentDate(), chargedOn.get())).sum();
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

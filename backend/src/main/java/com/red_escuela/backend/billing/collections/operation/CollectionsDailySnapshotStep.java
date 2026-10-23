package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.repository.CollectionsDailySnapshotStore;
import com.red_escuela.backend.billing.collections.repository.OverdueDebtFactsQuery;
import com.red_escuela.backend.billing.reporting.domain.CollectionsStockCalculator;
import com.red_escuela.backend.billing.reporting.domain.CollectionsStockRow;
import com.red_escuela.backend.billing.reporting.domain.OverdueInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CollectionsDailySnapshotStep {
    private static final Logger log = LoggerFactory.getLogger(CollectionsDailySnapshotStep.class);

    private final OverdueDebtFactsQuery factsQuery;
    private final CollectionsDailySnapshotStore store;
    private final Clock clock;
    private final AtomicLong lastSnapshotEpochDay = new AtomicLong(-1);

    public CollectionsDailySnapshotStep(OverdueDebtFactsQuery factsQuery, CollectionsDailySnapshotStore store, Clock clock, MeterRegistry meterRegistry) {
        this.factsQuery = factsQuery;
        this.store = store;
        this.clock = clock;
        Gauge.builder("billing.reporting.snapshot.last_date", lastSnapshotEpochDay, AtomicLong::get).register(meterRegistry);
    }

    @SchedulerLock(name = "CollectionsDailySnapshotStep", lockAtMostFor = "PT15M")
    public void takeCollectionsSnapshot(LocalDate businessDate) {
        List<OverdueInvoiceFact> overdueInvoices = factsQuery.findOverdueInvoicesOn(businessDate);
        List<CollectionsStockRow> rows = CollectionsStockCalculator.calculateStock(businessDate, overdueInvoices);
        Map<Currency, Money> unallocatedCredit = factsQuery.findUnallocatedCreditByCurrency();
        store.replaceSnapshotOf(businessDate, rows, unallocatedCredit, LocalDateTime.now(clock));
        lastSnapshotEpochDay.set(businessDate.toEpochDay());
        for (CollectionsStockRow total : rows.stream().filter(row -> row.scope().isTotal()).toList()) {
            log.info("Foto diaria de cobranza guardada: fecha={}, moneda={}, alcances={}, totalVencido={}", businessDate, total.currency(),
                    rows.stream().filter(row -> row.currency() == total.currency()).count(), total.totalOverdue().amount());
        }
    }
}

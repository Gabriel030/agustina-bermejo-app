package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.DirectInvoiceIssuanceStrategy;
import com.red_escuela.backend.billing.run.domain.DraftInvoiceIssuanceStrategy;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class MonthlyInvoiceIssuanceStrategies {

    private final Map<MonthlyInvoiceIssuanceMode, MonthlyInvoiceIssuanceStrategy> strategyByMode = new EnumMap<>(MonthlyInvoiceIssuanceMode.class);

    public MonthlyInvoiceIssuanceStrategies() {
        Stream.of(new DirectInvoiceIssuanceStrategy(), new DraftInvoiceIssuanceStrategy()).forEach(strategy -> strategyByMode.put(strategy.mode(), strategy));
    }

    public MonthlyInvoiceIssuanceStrategy strategyOf(MonthlyInvoiceIssuanceMode mode) {
        return strategyByMode.get(mode);
    }
}

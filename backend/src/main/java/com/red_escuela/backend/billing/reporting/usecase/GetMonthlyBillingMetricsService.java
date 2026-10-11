package com.red_escuela.backend.billing.reporting.usecase;

import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MonthRange;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.service.MetricCurrencyResolver;
import com.red_escuela.backend.billing.reporting.service.MetricQuery;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.time.ApplicationTime;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@UseCase
public class GetMonthlyBillingMetricsService implements GetMonthlyBillingMetricsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetMonthlyBillingMetricsService.class);
    private static final int DEFAULT_MONTHS = 12;

    private final Map<MetricPanel, MetricQuery> queriesByPanel = new EnumMap<>(MetricPanel.class);
    private final MetricCurrencyResolver currencyResolver;
    private final MeterRegistry meterRegistry;

    public GetMonthlyBillingMetricsService(List<MetricQuery> queries, MetricCurrencyResolver currencyResolver, MeterRegistry meterRegistry) {
        queries.forEach(query -> queriesByPanel.put(query.panel(), query));
        this.currencyResolver = currencyResolver;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public MonthlyMetric getMonthlyBillingMetrics(GetMonthlyBillingMetricsCommand command) {
        MetricPanel panel = MetricPanel.fromPath(command.panel());
        YearMonth last = Optional.ofNullable(command.to()).orElseGet(ApplicationTime::currentMonth);
        YearMonth first = Optional.ofNullable(command.from()).orElse(last.minusMonths(DEFAULT_MONTHS - 1L));
        MetricFilter filter = new MetricFilter(new MonthRange(first, last), currencyResolver.resolve(command.currency()), command.programId(),
                command.courseId(), command.invoiceType());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return queriesByPanel.get(panel).findSeries(filter);
        } catch (RuntimeException failure) {
            log.error("Falla al calcular el panel {} con el filtro {}", panel.path(), filter, failure);
            throw failure;
        } finally {
            sample.stop(Timer.builder("billing.reporting.query").tag("panel", panel.path()).register(meterRegistry));
        }
    }
}

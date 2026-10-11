package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MoneyValue;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class OverdueDetectedMetricQuery implements MetricQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public OverdueDetectedMetricQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.OVERDUE_DETECTED;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        InvoiceScope scope = InvoiceScope.of(filter, "i");
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare("count", MetricUnit.COUNT)
                .declare("amount", MetricUnit.MONEY);
        jdbc.query("select month, count(*) as invoices, coalesce(sum(total), 0) as amount from ("
                        + "select distinct to_char(coalesce(c.business_date, c.occurred_at::date), 'YYYY-MM') as month, i.id, i.total "
                        + "from invoice_status_change c join invoice i on i.id = c.invoice_id "
                        + "where c.status_kind = 'PAYMENT' and c.to_status = 'OVERDUE' and c.event = 'DUE_DATE_PASSED' "
                        + "and ((c.business_date >= :from and c.business_date < :to) "
                        + "or (c.business_date is null and c.occurred_at >= :fromTimestamp and c.occurred_at < :toTimestamp))"
                        + scope.conditions() + ") detected group by month order by month",
                scope.parameters(), row -> {
                    series.put("count", MonthlyRows.monthOf(row), new CountValue(row.getLong("invoices")));
                    series.put("amount", MonthlyRows.monthOf(row), new MoneyValue(MonthlyRows.moneyOf(row, "amount", filter.currency())));
                });
        return MonthlyMetric.available(panel(), filter.range(), series.build());
    }
}

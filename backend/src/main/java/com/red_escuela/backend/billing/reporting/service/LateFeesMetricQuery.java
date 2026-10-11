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

import java.util.Map;

@Component
@Transactional(readOnly = true)
public class LateFeesMetricQuery implements MetricQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public LateFeesMetricQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.LATE_FEES;
    }

    @Override
    public boolean isAvailable() {
        return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('late_fee_charge') is not null", Map.of(), Boolean.class));
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        if (!isAvailable()) {
            return MonthlyMetric.unavailable(panel(), filter.range());
        }
        InvoiceScope scope = InvoiceScope.of(filter, "i");
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare("issuedCount", MetricUnit.COUNT)
                .declare("issuedAmount", MetricUnit.MONEY)
                .declare("annulledCount", MetricUnit.COUNT)
                .declare("annulledAmount", MetricUnit.MONEY);
        jdbc.query("select to_char(i.issue_date, 'YYYY-MM') as month, count(*) as notes, coalesce(sum(i.total), 0) as amount "
                        + "from invoice i where i.document_status = 'ISSUED' and i.document_type = 'DEBIT_NOTE' and i.invoice_type = 'LATE_FEE' "
                        + "and i.issue_date >= :from and i.issue_date < :to" + scope.conditions() + " group by 1 order by 1",
                scope.parameters(), row -> {
                    series.put("issuedCount", MonthlyRows.monthOf(row), new CountValue(row.getLong("notes")));
                    series.put("issuedAmount", MonthlyRows.monthOf(row), new MoneyValue(MonthlyRows.moneyOf(row, "amount", filter.currency())));
                });
        jdbc.query("select to_char(n.issue_date, 'YYYY-MM') as month, count(*) as notes, coalesce(sum(n.total_amount), 0) as amount "
                        + "from credit_note n join invoice i on i.id = n.invoice_id "
                        + "where n.reason = 'LATE_FEE_ANNULMENT' and n.issue_date >= :from and n.issue_date < :to" + scope.conditions()
                        + " group by 1 order by 1",
                scope.parameters(), row -> {
                    series.put("annulledCount", MonthlyRows.monthOf(row), new CountValue(row.getLong("notes")));
                    series.put("annulledAmount", MonthlyRows.monthOf(row), new MoneyValue(MonthlyRows.moneyOf(row, "amount", filter.currency())));
                });
        return MonthlyMetric.available(panel(), filter.range(), series.build());
    }
}

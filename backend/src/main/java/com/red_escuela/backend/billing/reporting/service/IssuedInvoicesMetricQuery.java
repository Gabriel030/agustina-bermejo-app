package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.domain.MoneyValue;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class IssuedInvoicesMetricQuery implements MetricQuery {

    private static final String ISSUED_BY_THE_PROCESS = " from invoice i where i.document_status = 'ISSUED' "
            + "and i.issuing_reason in ('MONTHLY_RUN', 'COURSE_MONTHLY_RUN') and i.issue_date >= :from and i.issue_date < :to";

    private final NamedParameterJdbcTemplate jdbc;

    public IssuedInvoicesMetricQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.ISSUED_INVOICES;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        InvoiceScope scope = InvoiceScope.of(filter, "i");
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency()).declare("zeroTotalScholarship", MetricUnit.COUNT);
        jdbc.query("select to_char(i.issue_date, 'YYYY-MM') as month, i.invoice_type as type, count(*) as invoices, coalesce(sum(i.total), 0) as amount"
                        + ISSUED_BY_THE_PROCESS + scope.conditions() + " group by 1, 2 order by 1, 2",
                scope.parameters(), row -> {
                    String type = row.getString("type");
                    series.put("count." + type, MonthlyRows.monthOf(row), new CountValue(row.getLong("invoices")));
                    series.put("amount." + type, MonthlyRows.monthOf(row), new MoneyValue(MonthlyRows.moneyOf(row, "amount", filter.currency())));
                });
        jdbc.query("select to_char(i.issue_date, 'YYYY-MM') as month, count(*) as invoices" + ISSUED_BY_THE_PROCESS + scope.conditions()
                        + " and i.total = 0 and exists (select 1 from invoice_line zl join invoice_discount zd on zd.invoice_line_id = zl.id "
                        + "where zl.invoice_id = i.id and zd.discount_type = 'SCHOLARSHIP') group by 1",
                scope.parameters(), row -> {
                    series.put("zeroTotalScholarship", MonthlyRows.monthOf(row), new CountValue(row.getLong("invoices")));
                });
        return MonthlyMetric.available(panel(), filter.range(), series.build());
    }
}

package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MoneyRatio;
import com.red_escuela.backend.billing.reporting.domain.MoneyValue;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.domain.PercentValue;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class DiscountsMetricQuery implements MetricQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public DiscountsMetricQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.DISCOUNTS;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        InvoiceScope scope = InvoiceScope.of(filter, "i");
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare("totalForegone", MetricUnit.MONEY)
                .declare("percentOfGross", MetricUnit.PERCENT);
        Map<YearMonth, Money> foregoneByMonth = new HashMap<>();
        jdbc.query("select to_char(i.issue_date, 'YYYY-MM') as month, d.discount_type as type, count(*) as discounts, sum(d.discount_amount) as amount "
                        + "from invoice_discount d join invoice_line l on l.id = d.invoice_line_id join invoice i on i.id = l.invoice_id "
                        + "where i.document_status = 'ISSUED' and i.issue_date >= :from and i.issue_date < :to" + scope.conditions()
                        + " group by 1, 2 order by 1, 2",
                scope.parameters(), row -> {
                    YearMonth month = MonthlyRows.monthOf(row);
                    String type = row.getString("type");
                    Money amount = MonthlyRows.moneyOf(row, "amount", filter.currency());
                    series.put("count." + type, month, new CountValue(row.getLong("discounts")));
                    series.put("amount." + type, month, new MoneyValue(amount));
                    foregoneByMonth.merge(month, amount, Money::plus);
                });
        jdbc.query("select to_char(i.issue_date, 'YYYY-MM') as month, coalesce(sum(i.lines_total), 0) as gross "
                        + "from invoice i where i.document_status = 'ISSUED' and i.issue_date >= :from and i.issue_date < :to" + scope.conditions()
                        + " group by 1",
                scope.parameters(), row -> {
                    YearMonth month = MonthlyRows.monthOf(row);
                    Money foregone = foregoneByMonth.getOrDefault(month, Money.zero(filter.currency()));
                    series.put("totalForegone", month, new MoneyValue(foregone));
                    MoneyRatio.of(foregone, MonthlyRows.moneyOf(row, "gross", filter.currency()))
                            .ifPresent(percentage -> series.put("percentOfGross", month, new PercentValue(percentage)));
                });
        return MonthlyMetric.available(panel(), filter.range(), series.build());
    }
}

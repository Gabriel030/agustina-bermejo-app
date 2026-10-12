package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.DaysValue;
import com.red_escuela.backend.billing.reporting.domain.GoalIndicator;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MoneyValue;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.domain.PercentValue;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;

@Component
@Transactional(readOnly = true)
public class OnTimePaymentMetricQuery implements MetricQuery {

    static final String ON_TIME_PERCENT = "onTimePercent";

    private static final String CURRENT_ALLOCATION = "select a.invoice_id, a.effective_date, a.amount from payment_allocation a "
            + "where a.reverses_allocation_id is null and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = a.id)";

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardGoalsProvider goals;

    public OnTimePaymentMetricQuery(NamedParameterJdbcTemplate jdbc, DashboardGoalsProvider goals) {
        this.jdbc = jdbc;
        this.goals = goals;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.ON_TIME_PAYMENT;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        InvoiceScope scope = InvoiceScope.of(filter, "i");
        scope.parameters().addValue("today", Date.valueOf(ApplicationTime.today()));
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare(ON_TIME_PERCENT, MetricUnit.PERCENT)
                .declare("averageDelayDays", MetricUnit.DAYS)
                .declare("onTimeInvoices", MetricUnit.COUNT)
                .declare("consideredInvoices", MetricUnit.COUNT)
                .declare("collected", MetricUnit.MONEY);
        jdbc.query("with current_allocation as (" + CURRENT_ALLOCATION + ") "
                        + "select month, count(*) as considered, count(*) filter (where effective_date <= due_date) as on_time, "
                        + "avg(effective_date - due_date) filter (where effective_date > due_date) as average_delay from ("
                        + "select to_char(i.issue_date, 'YYYY-MM') as month, i.due_date, ca.effective_date "
                        + "from invoice i left join current_allocation ca on ca.invoice_id = i.id "
                        + "where i.document_status = 'ISSUED' and i.document_type = 'INVOICE' and i.total > 0 and i.payment_status <> 'CANCELLED' "
                        + "and i.issue_date >= :from and i.issue_date < :to"
                        + " and (ca.invoice_id is not null or (i.payment_status in ('PENDING', 'OVERDUE') and i.due_date < :today))"
                        + scope.conditions() + ") considered_invoices group by month order by month",
                scope.parameters(), row -> {
                    var month = MonthlyRows.monthOf(row);
                    long considered = row.getLong("considered");
                    long onTime = row.getLong("on_time");
                    series.put("consideredInvoices", month, new CountValue(considered));
                    series.put("onTimeInvoices", month, new CountValue(onTime));
                    Percentage.ratio(onTime, considered).ifPresent(percentage -> series.put(ON_TIME_PERCENT, month, new PercentValue(percentage)));
                    BigDecimal averageDelay = row.getBigDecimal("average_delay");
                    if (averageDelay != null) {
                        series.put("averageDelayDays", month, new DaysValue(averageDelay));
                    }
                });
        jdbc.query("with current_allocation as (" + CURRENT_ALLOCATION + ") "
                        + "select to_char(ca.effective_date, 'YYYY-MM') as month, sum(ca.amount) as collected "
                        + "from current_allocation ca join invoice i on i.id = ca.invoice_id "
                        + "where ca.effective_date >= :from and ca.effective_date < :to" + scope.conditions() + " group by 1 order by 1",
                scope.parameters(), row -> {
                    series.put("collected", MonthlyRows.monthOf(row), new MoneyValue(MonthlyRows.moneyOf(row, "collected", filter.currency())));
                });
        return MonthlyMetric.available(panel(), filter.range(), series.build())
                .withGoal(goals.findGoalInEffect(GoalIndicator.ON_TIME_PAYMENT), ON_TIME_PERCENT);
    }
}

package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.DaysValue;
import com.red_escuela.backend.billing.reporting.domain.GoalIndicator;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricGoal;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.domain.ReceiptOutcome;
import com.red_escuela.backend.billing.reporting.domain.ReceiptReviewTally;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Component
@Transactional(readOnly = true)
public class ReceiptsMetricQuery implements MetricQuery {

    static final String AVERAGE_REVIEW_DAYS = "averageReviewBusinessDays";

    private static final int DEFAULT_REVIEW_TARGET_BUSINESS_DAYS = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardGoalsProvider goals;

    public ReceiptsMetricQuery(NamedParameterJdbcTemplate jdbc, DashboardGoalsProvider goals) {
        this.jdbc = jdbc;
        this.goals = goals;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.RECEIPTS;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        InvoiceScope scope = InvoiceScope.of(filter, "i");
        Optional<MetricGoal> reviewGoal = goals.findGoalInEffect(GoalIndicator.RECEIPT_REVIEW_TIME);
        int reviewTarget = reviewGoal.map(goal -> goal.threshold().magnitude().intValue()).orElse(DEFAULT_REVIEW_TARGET_BUSINESS_DAYS);
        Map<YearMonth, ReceiptReviewTally> tallies = new TreeMap<>();
        jdbc.query("select to_char(r.uploaded_at, 'YYYY-MM') as month, r.status as status, r.uploaded_at::date as uploaded_on, r.reviewed_at::date as reviewed_on "
                        + "from bank_transfer_receipt r join invoice i on i.id = r.invoice_id "
                        + "where r.uploaded_at >= :fromTimestamp and r.uploaded_at < :toTimestamp" + scope.conditions(),
                scope.parameters(), row -> {
                    Date reviewedOn = row.getDate("reviewed_on");
                    tallies.computeIfAbsent(MonthlyRows.monthOf(row), ignored -> new ReceiptReviewTally(reviewTarget))
                            .recordUpload(ReceiptOutcome.valueOf(row.getString("status")), row.getDate("uploaded_on").toLocalDate(),
                                    reviewedOn == null ? null : reviewedOn.toLocalDate());
                });
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare("uploaded", MetricUnit.COUNT)
                .declare("approved", MetricUnit.COUNT)
                .declare("appliedToDebt", MetricUnit.COUNT)
                .declare("rejected", MetricUnit.COUNT)
                .declare("reviewedAfterTarget", MetricUnit.COUNT)
                .declare(AVERAGE_REVIEW_DAYS, MetricUnit.DAYS);
        tallies.forEach((month, tally) -> {
            series.put("uploaded", month, new CountValue(tally.uploaded()));
            series.put("approved", month, new CountValue(tally.approved()));
            series.put("appliedToDebt", month, new CountValue(tally.appliedToDebt()));
            series.put("rejected", month, new CountValue(tally.rejected()));
            series.put("reviewedAfterTarget", month, new CountValue(tally.reviewedAfterTarget()));
            tally.averageReviewBusinessDays().ifPresent(average -> series.put(AVERAGE_REVIEW_DAYS, month, average));
        });
        return MonthlyMetric.available(panel(), filter.range(), series.build()).withGoal(reviewGoal, AVERAGE_REVIEW_DAYS);
    }
}

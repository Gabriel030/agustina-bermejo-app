package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MissedNights;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

@Component
@Transactional(readOnly = true)
public class NightlyHealthMetricQuery implements MetricQuery {

    static final String NIGHTS_COMPLETED = "nightsCompleted";
    static final String NIGHTS_COMPLETED_WITH_ERRORS = "nightsCompletedWithErrors";
    static final String NIGHTS_FAILED = "nightsFailed";
    static final String NIGHTS_MISSED = "nightsMissed";
    static final String AVERAGE_DURATION_SECONDS = "averageDurationSeconds";
    static final String LONGEST_DURATION_SECONDS = "longestDurationSeconds";
    static final String HELD_ACCOUNTS = "heldAccounts";
    static final String SKIPPED_ITEMS_PREFIX = "skippedItems.";
    static final String FAILED_ITEMS_PREFIX = "failedItems.";

    private static final String HELD_ACCOUNT_DECISION_CODE = "INCONSISTENT_ACCOUNT";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public NightlyHealthMetricQuery(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.NIGHTLY_HEALTH;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        if (filter.program().isPresent() || filter.course().isPresent()) {
            return MonthlyMetric.unavailable(panel(), filter.range());
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(filter.range().firstDay()))
                .addValue("to", Date.valueOf(filter.range().dayAfterLastDay()));
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency())
                .declare(NIGHTS_COMPLETED, MetricUnit.COUNT)
                .declare(NIGHTS_COMPLETED_WITH_ERRORS, MetricUnit.COUNT)
                .declare(NIGHTS_FAILED, MetricUnit.COUNT)
                .declare(NIGHTS_MISSED, MetricUnit.COUNT)
                .declareLeavingMonthsWithoutDataEmpty(AVERAGE_DURATION_SECONDS, MetricUnit.COUNT)
                .declareLeavingMonthsWithoutDataEmpty(LONGEST_DURATION_SECONDS, MetricUnit.COUNT)
                .declare(HELD_ACCOUNTS, MetricUnit.COUNT);
        for (BillingRunStep step : BillingRunStep.values()) {
            series.declare(SKIPPED_ITEMS_PREFIX + step.name(), MetricUnit.COUNT);
            series.declare(FAILED_ITEMS_PREFIX + step.name(), MetricUnit.COUNT);
        }
        jdbc.query("select to_char(n.business_date, 'YYYY-MM') as month, "
                        + "count(*) filter (where n.status = 'COMPLETED') as completed, "
                        + "count(*) filter (where n.status = 'COMPLETED_WITH_ERRORS') as completed_with_errors, "
                        + "count(*) filter (where n.status = 'FAILED') as failed, "
                        + "avg(extract(epoch from n.finished_at - n.started_at)) as average_seconds, "
                        + "max(extract(epoch from n.finished_at - n.started_at)) as longest_seconds "
                        + "from nightly_billing_run n where n.business_date >= :from and n.business_date < :to group by 1 order by 1",
                parameters, row -> {
                    YearMonth month = MonthlyRows.monthOf(row);
                    series.put(NIGHTS_COMPLETED, month, new CountValue(row.getLong("completed")));
                    series.put(NIGHTS_COMPLETED_WITH_ERRORS, month, new CountValue(row.getLong("completed_with_errors")));
                    series.put(NIGHTS_FAILED, month, new CountValue(row.getLong("failed")));
                    putSeconds(series, AVERAGE_DURATION_SECONDS, month, row.getBigDecimal("average_seconds"));
                    putSeconds(series, LONGEST_DURATION_SECONDS, month, row.getBigDecimal("longest_seconds"));
                });
        jdbc.query("select to_char(n.business_date, 'YYYY-MM') as month, r.step as step, sum(r.skipped) as skipped, sum(r.failed) as failed "
                        + "from billing_run r join nightly_billing_run n on n.id = r.nightly_billing_run_id "
                        + "where n.business_date >= :from and n.business_date < :to group by 1, 2 order by 1, 2",
                parameters, row -> {
                    YearMonth month = MonthlyRows.monthOf(row);
                    series.put(SKIPPED_ITEMS_PREFIX + row.getString("step"), month, new CountValue(row.getLong("skipped")));
                    series.put(FAILED_ITEMS_PREFIX + row.getString("step"), month, new CountValue(row.getLong("failed")));
                });
        MapSqlParameterSource heldParameters = new MapSqlParameterSource(parameters.getValues()).addValue("heldCode", HELD_ACCOUNT_DECISION_CODE);
        jdbc.query("select to_char(n.business_date, 'YYYY-MM') as month, count(distinct i.subject_id) as held "
                        + "from billing_run_item i join billing_run r on r.id = i.billing_run_id "
                        + "join nightly_billing_run n on n.id = r.nightly_billing_run_id "
                        + "where i.decision_code = :heldCode and n.business_date >= :from and n.business_date < :to group by 1 order by 1",
                heldParameters, row -> {
                    series.put(HELD_ACCOUNTS, MonthlyRows.monthOf(row), new CountValue(row.getLong("held")));
                });
        putMissedNights(series, filter, parameters);
        return MonthlyMetric.available(panel(), filter.range(), series.build());
    }

    private void putMissedNights(MetricSeriesBuilder series, MetricFilter filter, MapSqlParameterSource parameters) {
        LocalDate firstRecordedNight = jdbc.queryForObject("select min(business_date) from nightly_billing_run", parameters, LocalDate.class);
        if (firstRecordedNight == null) {
            return;
        }
        Set<LocalDate> recordedNights = new HashSet<>(jdbc.queryForList(
                "select business_date from nightly_billing_run where business_date >= :from and business_date < :to", parameters, LocalDate.class));
        LocalDate today = LocalDate.now(clock);
        for (YearMonth month : filter.range().months()) {
            series.put(NIGHTS_MISSED, month, new CountValue(MissedNights.countIn(month, firstRecordedNight, today, recordedNights)));
        }
    }

    private static void putSeconds(MetricSeriesBuilder series, String key, YearMonth month, BigDecimal seconds) {
        if (seconds != null) {
            series.put(key, month, new CountValue(seconds.setScale(0, RoundingMode.HALF_UP).longValueExact()));
        }
    }
}

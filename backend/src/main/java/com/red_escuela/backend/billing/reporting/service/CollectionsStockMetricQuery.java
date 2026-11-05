package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MetricSeriesBuilder;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MoneyValue;
import com.red_escuela.backend.billing.reporting.domain.MonthEndSnapshotChoice;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.domain.OverdueAgeBucket;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class CollectionsStockMetricQuery implements MetricQuery {

    private static final String TOTAL_KEY = "amount.TOTAL";
    private static final String STUDENTS_KEY = "students";

    private final NamedParameterJdbcTemplate jdbc;

    public CollectionsStockMetricQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MetricPanel panel() {
        return MetricPanel.COLLECTIONS_STOCK;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public MonthlyMetric findSeries(MetricFilter filter) {
        if (filter.program().isPresent() && filter.course().isPresent()) {
            return MonthlyMetric.unavailable(panel(), filter.range());
        }
        Map<YearMonth, MonthEndSnapshotChoice> choiceByMonth = chooseSnapshotOfEachMonth(filter);
        MetricSeriesBuilder series = declareSeries(filter);
        if (!choiceByMonth.isEmpty()) {
            Map<LocalDate, YearMonth> monthByDate = new HashMap<>();
            choiceByMonth.forEach((month, choice) -> monthByDate.put(choice.snapshotDate(), month));
            choiceByMonth.keySet().forEach(month -> fillWithZeros(series, month, filter.currency()));
            readRows(filter, monthByDate, series);
        }
        boolean partial = choiceByMonth.values().stream().anyMatch(MonthEndSnapshotChoice::partial);
        return MonthlyMetric.available(panel(), filter.range(), series.build()).withPartial(partial);
    }

    private Map<YearMonth, MonthEndSnapshotChoice> chooseSnapshotOfEachMonth(MetricFilter filter) {
        List<LocalDate> snapshotDates = jdbc.queryForList("select business_date from collections_daily_snapshot where currency = :currency "
                        + "and program_id is null and course_id is null and business_date >= :from and business_date < :to",
                new MapSqlParameterSource().addValue("currency", filter.currency().name())
                        .addValue("from", Date.valueOf(filter.range().firstDay())).addValue("to", Date.valueOf(filter.range().dayAfterLastDay())), LocalDate.class);
        Map<YearMonth, MonthEndSnapshotChoice> choices = new HashMap<>();
        for (YearMonth month : filter.range().months()) {
            Optional<MonthEndSnapshotChoice> choice = MonthEndSnapshotChoice.choose(month, snapshotDates);
            choice.ifPresent(chosen -> choices.put(month, chosen));
        }
        return choices;
    }

    private MetricSeriesBuilder declareSeries(MetricFilter filter) {
        MetricSeriesBuilder series = new MetricSeriesBuilder(filter.range(), filter.currency());
        for (OverdueAgeBucket bucket : OverdueAgeBucket.values()) {
            series.declareLeavingMonthsWithoutDataEmpty(keyOf(bucket), MetricUnit.MONEY);
        }
        series.declareLeavingMonthsWithoutDataEmpty(TOTAL_KEY, MetricUnit.MONEY);
        series.declareLeavingMonthsWithoutDataEmpty(STUDENTS_KEY, MetricUnit.COUNT);
        return series;
    }

    private void fillWithZeros(MetricSeriesBuilder series, YearMonth month, Currency currency) {
        for (OverdueAgeBucket bucket : OverdueAgeBucket.values()) {
            series.put(keyOf(bucket), month, new MoneyValue(Money.zero(currency)));
        }
        series.put(TOTAL_KEY, month, new MoneyValue(Money.zero(currency)));
        series.put(STUDENTS_KEY, month, new CountValue(0));
    }

    private void readRows(MetricFilter filter, Map<LocalDate, YearMonth> monthByDate, MetricSeriesBuilder series) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("currency", filter.currency().name())
                .addValue("dates", new ArrayList<>(monthByDate.keySet().stream().map(Date::valueOf).toList()));
        String scope = " and program_id is null and course_id is null";
        if (filter.program().isPresent()) {
            scope = " and program_id = :programId";
            parameters.addValue("programId", filter.program().get());
        } else if (filter.course().isPresent()) {
            scope = " and course_id = :courseId";
            parameters.addValue("courseId", filter.course().get());
        }
        jdbc.query("select business_date, overdue_1_30, overdue_31_60, overdue_61_90, overdue_90_plus, students_with_overdue from collections_daily_snapshot "
                + "where currency = :currency and business_date in (:dates)" + scope, parameters, row -> {
            YearMonth month = monthByDate.get(row.getDate("business_date").toLocalDate());
            BigDecimal[] amounts = {row.getBigDecimal("overdue_1_30"), row.getBigDecimal("overdue_31_60"), row.getBigDecimal("overdue_61_90"),
                    row.getBigDecimal("overdue_90_plus")};
            Money total = Money.zero(filter.currency());
            OverdueAgeBucket[] buckets = OverdueAgeBucket.values();
            for (int index = 0; index < buckets.length; index++) {
                Money amount = Money.of(amounts[index], filter.currency());
                series.put(keyOf(buckets[index]), month, new MoneyValue(amount));
                total = total.plus(amount);
            }
            series.put(TOTAL_KEY, month, new MoneyValue(total));
            series.put(STUDENTS_KEY, month, new CountValue(row.getLong("students_with_overdue")));
        });
    }

    private static String keyOf(OverdueAgeBucket bucket) {
        return "amount." + bucket.name();
    }
}

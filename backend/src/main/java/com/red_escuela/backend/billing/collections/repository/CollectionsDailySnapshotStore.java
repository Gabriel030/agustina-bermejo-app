package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.billing.reporting.domain.CollectionsStockRow;
import com.red_escuela.backend.billing.reporting.domain.OverdueAgeBucket;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class CollectionsDailySnapshotStore {

    private static final long ADVISORY_LOCK_NAMESPACE = 7_019_000_000L;

    private final NamedParameterJdbcTemplate jdbc;

    public CollectionsDailySnapshotStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceSnapshotOf(LocalDate businessDate, List<CollectionsStockRow> rows, Map<Currency, Money> unallocatedCreditByCurrency, LocalDateTime recordedAt) {
        jdbc.query("select pg_advisory_xact_lock(:key)", new MapSqlParameterSource("key", ADVISORY_LOCK_NAMESPACE + businessDate.toEpochDay()), (java.sql.ResultSet lockAcquired) -> {
        });
        jdbc.update("delete from collections_daily_snapshot where business_date = :businessDate", new MapSqlParameterSource("businessDate", Date.valueOf(businessDate)));
        MapSqlParameterSource[] batch = rows.stream().map(row -> parametersOf(row, unallocatedCreditByCurrency, recordedAt)).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("insert into collections_daily_snapshot (business_date, currency, program_id, course_id, overdue_1_30, overdue_31_60, overdue_61_90, "
                + "overdue_90_plus, students_with_overdue, unallocated_credit_amount, created_at) values (:businessDate, :currency, :programId, :courseId, "
                + ":overdue1To30, :overdue31To60, :overdue61To90, :overdue90Plus, :students, :credit, :recordedAt)", batch);
    }

    private static MapSqlParameterSource parametersOf(CollectionsStockRow row, Map<Currency, Money> unallocatedCreditByCurrency, LocalDateTime recordedAt) {
        Money credit = unallocatedCreditByCurrency.get(row.currency());
        return new MapSqlParameterSource()
                .addValue("businessDate", Date.valueOf(row.businessDate()))
                .addValue("currency", row.currency().name())
                .addValue("programId", row.scope().programId())
                .addValue("courseId", row.scope().courseId())
                .addValue("overdue1To30", row.overdueIn(OverdueAgeBucket.FROM_1_TO_30).amount())
                .addValue("overdue31To60", row.overdueIn(OverdueAgeBucket.FROM_31_TO_60).amount())
                .addValue("overdue61To90", row.overdueIn(OverdueAgeBucket.FROM_61_TO_90).amount())
                .addValue("overdue90Plus", row.overdueIn(OverdueAgeBucket.OVER_90).amount())
                .addValue("students", row.studentsWithOverdue())
                .addValue("credit", row.scope().isTotal() && credit != null ? credit.amount() : null)
                .addValue("recordedAt", Timestamp.valueOf(recordedAt));
    }
}

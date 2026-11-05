package com.red_escuela.backend.billing.collections.repository;

import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CollectionReminderRunStore {

    private final NamedParameterJdbcTemplate jdbc;

    public CollectionReminderRunStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordCoveredDay(LocalDate businessDate) {
        jdbc.update("insert into collection_reminder_run (business_date, completed_at) values (:businessDate, :completedAt) on conflict (business_date) do nothing",
                new MapSqlParameterSource().addValue("businessDate", Date.valueOf(businessDate)).addValue("completedAt", Timestamp.valueOf(ApplicationTime.now())));
    }

    public List<LocalDate> findRunDatesBetween(LocalDate from, LocalDate toInclusive) {
        return jdbc.queryForList("select business_date from collection_reminder_run where business_date between :from and :to",
                new MapSqlParameterSource().addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(toInclusive)), Date.class)
                .stream().map(Date::toLocalDate).toList();
    }

    public Optional<LocalDate> findEarliestRunDate() {
        return jdbc.queryForList("select min(business_date) from collection_reminder_run", new MapSqlParameterSource(), Date.class)
                .stream().filter(Objects::nonNull).map(Date::toLocalDate).findFirst();
    }
}

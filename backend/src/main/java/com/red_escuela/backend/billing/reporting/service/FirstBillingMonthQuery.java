package com.red_escuela.backend.billing.reporting.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class FirstBillingMonthQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public FirstBillingMonthQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<YearMonth> findFirstBillingMonth() {
        LocalDate earliest = jdbc.queryForObject("select min(i.issue_date) from invoice i", Map.of(), LocalDate.class);
        return Optional.ofNullable(earliest).map(YearMonth::from);
    }
}

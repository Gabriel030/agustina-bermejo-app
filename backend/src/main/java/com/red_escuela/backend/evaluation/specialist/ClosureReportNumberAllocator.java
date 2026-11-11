package com.red_escuela.backend.evaluation.specialist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class ClosureReportNumberAllocator {

    private static final String NEXT_NUMBER_OF_YEAR = "insert into closure_report_number_counter (report_year, last_number) "
            + "values (?, 1) on conflict (report_year) do update "
            + "set last_number = closure_report_number_counter.last_number + 1 returning last_number";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ClosureReportNumberAllocator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocateNextReportNumber() {
        int year = LocalDate.now(clock).getYear();
        Integer number = jdbcTemplate.queryForObject(NEXT_NUMBER_OF_YEAR, Integer.class, year);
        return String.format("ACTA-%d-%04d", year, number);
    }
}

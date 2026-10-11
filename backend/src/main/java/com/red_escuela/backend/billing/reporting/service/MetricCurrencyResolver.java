package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.exception.CurrencyRequiredException;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class MetricCurrencyResolver {

    private final JdbcTemplate jdbc;

    public MetricCurrencyResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Currency resolve(Currency requested) {
        if (requested != null) {
            return requested;
        }
        List<Currency> inUse = jdbc.queryForList("select distinct currency from student_current_account order by currency", String.class)
                .stream().map(Currency::valueOf).toList();
        if (inUse.size() > 1) {
            throw new CurrencyRequiredException();
        }
        return inUse.isEmpty() ? Currency.ARS : inUse.get(0);
    }
}

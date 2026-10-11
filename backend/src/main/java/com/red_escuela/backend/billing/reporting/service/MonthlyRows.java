package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;

final class MonthlyRows {

    private MonthlyRows() {
    }

    static YearMonth monthOf(ResultSet row) throws SQLException {
        return YearMonth.parse(row.getString("month"));
    }

    static Money moneyOf(ResultSet row, String column, Currency currency) throws SQLException {
        BigDecimal amount = row.getBigDecimal(column);
        return Money.of(amount == null ? BigDecimal.ZERO : amount, currency);
    }
}

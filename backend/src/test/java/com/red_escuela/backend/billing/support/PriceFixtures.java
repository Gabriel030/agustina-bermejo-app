package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

public final class PriceFixtures {

    private PriceFixtures() {
    }

    public static Long persistPriceVersion(Long programId, Long courseId, FeeType feeType, String description, Money amount, LocalDate effectiveFrom, Long createdById) {
        return persistPriceVersion(programId, courseId, feeType, description, amount, effectiveFrom, createdById, null, null);
    }

    public static Long persistPriceVersion(Long programId, Long courseId, FeeType feeType, String description, Money amount, LocalDate effectiveFrom, Long createdById,
                                           String marketingDiscountType, java.math.BigDecimal marketingDiscountValue) {
        JdbcTemplate jdbc = BillingTestApplicationContext.bean(JdbcTemplate.class);
        Long priceId = findOrInsertPrice(jdbc, programId, courseId, feeType, description, amount);
        return jdbc.queryForObject("insert into price_version (price_id, amount, amount_currency, effective_from, status, change_reason, proposed_by_id, proposed_at, "
                + "decided_by_id, decided_at, direct_publication, marketing_discount_type, marketing_discount_value) "
                + "values (?, ?, ?, ?, case when cast(? as date) > current_date then 'SCHEDULED' else 'EFFECTIVE' end, 'Fixture', ?, now(), ?, now(), true, ?, ?) returning id",
                Long.class, priceId, amount.amount(), amount.currency().name(), effectiveFrom, effectiveFrom, createdById, createdById, marketingDiscountType, marketingDiscountValue);
    }

    private static Long findOrInsertPrice(JdbcTemplate jdbc, Long programId, Long courseId, FeeType feeType, String description, Money amount) {
        List<Long> found = jdbc.queryForList("select id from price where fee_type = ? and program_id is not distinct from ? and course_id is not distinct from ?",
                Long.class, feeType.name(), programId, courseId);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        return jdbc.queryForObject("insert into price (program_id, course_id, fee_type, currency, description) values (?, ?, ?, ?, ?) returning id",
                Long.class, programId, courseId, feeType.name(), amount.currency().name(), description);
    }
}

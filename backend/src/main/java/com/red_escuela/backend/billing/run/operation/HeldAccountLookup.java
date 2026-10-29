package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.HeldSubjectFinder;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class HeldAccountLookup {
    private static final String HELD_ACCOUNTS_OF_THE_NIGHT = """
            select item.subject_id from billing_run_item item
            join billing_run step_run on step_run.id = item.billing_run_id
            join nightly_billing_run night on night.id = step_run.nightly_billing_run_id
            where night.business_date = :businessDate and step_run.step = :step and item.subject_type = :subjectType
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HeldAccountLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Set<Long> findHeldAccountIds(LocalDate businessDate) {
        return new HashSet<>(jdbc.queryForList(HELD_ACCOUNTS_OF_THE_NIGHT, parametersOf(businessDate), Long.class));
    }

    public HeldSubjectFinder heldInvoicesOf(LocalDate businessDate) {
        return invoiceIds -> findHeldAmong("invoice", invoiceIds, businessDate);
    }

    public HeldSubjectFinder heldLateFeeChargesOf(LocalDate businessDate) {
        return chargeIds -> findHeldAmong("late_fee_charge", chargeIds, businessDate);
    }

    private Set<Long> findHeldAmong(String table, Collection<Long> subjectIds, LocalDate businessDate) {
        if (subjectIds.isEmpty()) {
            return Set.of();
        }
        String sql = "select s.id from " + table + " s where s.id in (:ids) and s.student_current_account_id in (" + HELD_ACCOUNTS_OF_THE_NIGHT + ")";
        return new HashSet<>(jdbc.queryForList(sql, parametersOf(businessDate).addValue("ids", subjectIds), Long.class));
    }

    private static MapSqlParameterSource parametersOf(LocalDate businessDate) {
        return new MapSqlParameterSource("businessDate", businessDate)
                .addValue("step", BillingRunStep.ACCOUNT_CONSISTENCY.name())
                .addValue("subjectType", BillingRunSubjectType.CURRENT_ACCOUNT.name());
    }
}

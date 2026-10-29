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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HeldAccountLookup {
    private static final String HELD_ACCOUNTS_OF_THE_NIGHT = """
            select held.account_id, held.code from (
                select item.subject_id as account_id, cast(:inconsistentCode as varchar) as code, 1 as priority
                from billing_run_item item
                join billing_run step_run on step_run.id = item.billing_run_id
                join nightly_billing_run night on night.id = step_run.nightly_billing_run_id
                where night.business_date = :businessDate and step_run.step = :consistencyStep and item.subject_type = :accountType
                union all
                select item.subject_id, :failedCode, 2
                from billing_run_item item
                join billing_run step_run on step_run.id = item.billing_run_id
                join nightly_billing_run night on night.id = step_run.nightly_billing_run_id
                where night.business_date = :businessDate and step_run.step <> :consistencyStep and item.status = 'FAILED' and item.subject_type = :accountType
                union all
                select i.student_current_account_id, :failedCode, 2
                from billing_run_item item
                join billing_run step_run on step_run.id = item.billing_run_id
                join nightly_billing_run night on night.id = step_run.nightly_billing_run_id
                join invoice i on i.id = item.subject_id
                where night.business_date = :businessDate and step_run.step <> :consistencyStep and item.status = 'FAILED' and item.subject_type = :invoiceType
                union all
                select c.student_current_account_id, :failedCode, 2
                from billing_run_item item
                join billing_run step_run on step_run.id = item.billing_run_id
                join nightly_billing_run night on night.id = step_run.nightly_billing_run_id
                join late_fee_charge c on c.id = item.subject_id
                where night.business_date = :businessDate and step_run.step <> :consistencyStep and item.status = 'FAILED' and item.subject_type = :chargeType
            ) held
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HeldAccountLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findHeldAccountCodes(LocalDate businessDate) {
        Map<Long, String> codesByAccount = new LinkedHashMap<>();
        jdbc.query(HELD_ACCOUNTS_OF_THE_NIGHT, parametersOf(businessDate), rs -> {
            codesByAccount.merge(rs.getLong(1), rs.getString(2), HeldAccountLookup::strongerHoldOf);
        });
        return codesByAccount;
    }

    public HeldSubjectFinder heldInvoicesOf(LocalDate businessDate) {
        return invoiceIds -> findHeldAmong("invoice", invoiceIds, businessDate);
    }

    public HeldSubjectFinder heldLateFeeChargesOf(LocalDate businessDate) {
        return chargeIds -> findHeldAmong("late_fee_charge", chargeIds, businessDate);
    }

    private Map<Long, String> findHeldAmong(String table, Collection<Long> subjectIds, LocalDate businessDate) {
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        String sql = "select s.id, held.code from " + table + " s join (" + HELD_ACCOUNTS_OF_THE_NIGHT + ") held on held.account_id = s.student_current_account_id where s.id in (:ids)";
        Map<Long, String> codesBySubject = new LinkedHashMap<>();
        jdbc.query(sql, parametersOf(businessDate).addValue("ids", subjectIds), rs -> {
            codesBySubject.merge(rs.getLong(1), rs.getString(2), HeldAccountLookup::strongerHoldOf);
        });
        return codesBySubject;
    }

    static String strongerHoldOf(String first, String second) {
        return HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE.equals(first) || HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE.equals(second)
                ? HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE : first;
    }

    private static MapSqlParameterSource parametersOf(LocalDate businessDate) {
        return new MapSqlParameterSource("businessDate", businessDate)
                .addValue("consistencyStep", BillingRunStep.ACCOUNT_CONSISTENCY.name())
                .addValue("accountType", BillingRunSubjectType.CURRENT_ACCOUNT.name())
                .addValue("invoiceType", BillingRunSubjectType.INVOICE.name())
                .addValue("chargeType", BillingRunSubjectType.LATE_FEE_CHARGE.name())
                .addValue("inconsistentCode", HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE)
                .addValue("failedCode", HeldSubjectFinder.PREVIOUS_STEP_FAILED_CODE);
    }
}

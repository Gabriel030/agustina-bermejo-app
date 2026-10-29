package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.AccountConsistencyCheck;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyFinding;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class AccountConsistencyScanner {
    public static final String NO_SCOPE = "true";

    private final JdbcTemplate jdbc;

    public AccountConsistencyScanner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AccountConsistencyScan scanAllAccounts(LocalDate businessDate) {
        int total = jdbc.queryForObject("select count(*) from student_current_account", Integer.class);
        List<AccountConsistencyFinding> findings = new ArrayList<>();
        AccountConsistencyCheck.inPriorityOrder().forEach(check -> findings.addAll(findingsOf(check, businessDate)));
        return new AccountConsistencyScan(total, findings);
    }

    private List<AccountConsistencyFinding> findingsOf(AccountConsistencyCheck check, LocalDate businessDate) {
        String sql = check.sql().replace("@INVOICES@", NO_SCOPE).replace("@ACCOUNTS@", NO_SCOPE);
        Object[] arguments = check.takesBusinessDate() ? new Object[]{businessDate} : new Object[0];
        return jdbc.query(sql, (rs, rowNum) -> new AccountConsistencyFinding(rs.getLong(1), check.findingCode(), rs.getString(2)), arguments);
    }
}

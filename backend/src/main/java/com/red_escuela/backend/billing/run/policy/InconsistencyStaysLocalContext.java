package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.AccountConsistencyScan;

public record InconsistencyStaysLocalContext(int totalAccounts, int inconsistentAccounts) {

    public static InconsistencyStaysLocalContext of(AccountConsistencyScan scan) {
        return new InconsistencyStaysLocalContext(scan.totalAccounts(), scan.inconsistentAccounts());
    }
}

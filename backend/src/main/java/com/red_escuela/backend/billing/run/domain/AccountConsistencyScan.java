package com.red_escuela.backend.billing.run.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AccountConsistencyScan(int totalAccounts, List<AccountConsistencyFinding> findings) {

    public AccountConsistencyScan {
        Map<Long, AccountConsistencyFinding> firstPerAccount = new LinkedHashMap<>();
        findings.forEach(finding -> firstPerAccount.putIfAbsent(finding.accountId(), finding));
        findings = List.copyOf(firstPerAccount.values());
    }

    public int inconsistentAccounts() {
        return findings.size();
    }
}

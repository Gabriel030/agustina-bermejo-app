package com.red_escuela.backend.billing.planning.policy;

import java.time.YearMonth;
import java.util.Set;

public interface InstallmentPeriodContext {
    YearMonth period();

    Set<YearMonth> issuedPeriods();
}

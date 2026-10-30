package com.red_escuela.backend.billing.run.policy;

import java.time.YearMonth;

public record ManualIssuanceWithinCurrentMonthContext(YearMonth requestedPeriod, YearMonth currentPeriod) {
}

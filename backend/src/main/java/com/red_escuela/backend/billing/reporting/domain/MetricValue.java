package com.red_escuela.backend.billing.reporting.domain;

import java.math.BigDecimal;

public sealed interface MetricValue permits CountValue, MoneyValue, PercentValue, DaysValue {

    MetricUnit unit();

    BigDecimal magnitude();
}

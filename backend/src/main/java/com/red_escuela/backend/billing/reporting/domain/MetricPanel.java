package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.billing.reporting.exception.UnknownMetricPanelException;

import java.util.Arrays;

public enum MetricPanel {
    ISSUED_INVOICES("issued-invoices"),
    DISCOUNTS("discounts"),
    OVERDUE_DETECTED("overdue-detected"),
    LATE_FEES("late-fees"),
    ON_TIME_PAYMENT("on-time-payment"),
    RECEIPTS("receipts"),
    COLLECTIONS_STOCK("collections-stock");

    private final String path;

    MetricPanel(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static MetricPanel fromPath(String path) {
        return Arrays.stream(values())
                .filter(panel -> panel.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new UnknownMetricPanelException(path));
    }
}

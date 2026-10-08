package com.red_escuela.backend.architecture.fixture.planning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingFixtureMonthlyInvoicePlanner {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingFixtureMonthlyInvoicePlanner.class);

    public void planInvoicesForMonth() {
        LOG.info("planning");
    }
}

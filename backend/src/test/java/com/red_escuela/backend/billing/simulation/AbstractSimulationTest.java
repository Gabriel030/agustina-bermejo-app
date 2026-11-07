package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.billing.run.operation.FailedInvoiceRetryJob;
import com.red_escuela.backend.billing.run.operation.MonthlyInvoiceIssuanceJob;
import com.red_escuela.backend.billing.run.operation.NightlyBillingJob;
import com.red_escuela.backend.billing.service.BillingTestTrigger;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public abstract class AbstractSimulationTest extends AbstractIntegrationTest {

    @Autowired
    protected ApplicationContext applicationContext;
    @Autowired
    protected EntityManager entityManager;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ConfigurationVersionRepository configurationVersionRepository;
    @Autowired
    protected BillingTestTrigger billingTestTrigger;
    @Autowired
    protected PreviewCashPaymentUseCase previewCashPaymentUseCase;
    @Autowired
    protected RegisterCashPaymentUseCase registerCashPaymentUseCase;

    protected Fixtures fixtures() {
        return new Fixtures(entityManager, transactionTemplate, configurationVersionRepository, jdbcTemplate, billingTestTrigger);
    }

    protected Timeline timeline(Timeline.Mode mode) {
        return Timeline.of(applicationContext, mode, MutableClock.INSTANCE, NightlyBillingJob.class, MonthlyInvoiceIssuanceJob.class, FailedInvoiceRetryJob.class);
    }

    protected Scenario scenario(Timeline.Mode mode) {
        Fixtures fixtures = fixtures();
        Fixtures.Catalog catalog = fixtures.standardCatalog();
        fixtures.configureLatePayment(catalog);
        return new Scenario(fixtures, catalog, timeline(mode).startAt(Fixtures.SCENARIO_START), jdbcTemplate, MutableClock.INSTANCE, previewCashPaymentUseCase, registerCashPaymentUseCase,
                ledgerTable -> SimulationInvariants.forDatabase(jdbcTemplate, MutableClock.INSTANCE, ledgerTable));
    }
}

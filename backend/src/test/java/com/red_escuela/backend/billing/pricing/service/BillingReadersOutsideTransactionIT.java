package com.red_escuela.backend.billing.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.red_escuela.backend.billing.reporting.service.DebtorListingReader;
import com.red_escuela.backend.billing.reporting.service.FinancialDashboardReader;
import com.red_escuela.backend.billing.reporting.service.OwnCourseEnrollmentBillingStatusReader;
import com.red_escuela.backend.billing.reporting.service.OwnProgramEnrollmentBillingStatusReader;
import com.red_escuela.backend.billing.reporting.service.StudentBillingOverviewReader;
import com.red_escuela.backend.billing.run.service.BillingRunReader;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.notification.delivery.specialist.SimulatedEmailListingReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

@SpringBootTest
@ActiveProfiles("test")
class BillingReadersOutsideTransactionIT extends AbstractIntegrationTest {

    @Autowired
    private PriceListingReader priceListingReader;
    @Autowired
    private BillingRunReader billingRunReader;
    @Autowired
    private SimulatedEmailListingReader simulatedEmailListingReader;
    @Autowired
    private FinancialDashboardReader financialDashboardReader;
    @Autowired
    private DebtorListingReader debtorListingReader;
    @Autowired
    private OwnCourseEnrollmentBillingStatusReader ownCourseEnrollmentBillingStatusReader;
    @Autowired
    private OwnProgramEnrollmentBillingStatusReader ownProgramEnrollmentBillingStatusReader;
    @Autowired
    private StudentBillingOverviewReader studentBillingOverviewReader;
    @MockitoSpyBean
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void forgetPreviousTransactions() {
        clearInvocations(transactionManager);
    }

    @Test
    void listPricesOpensReadOnlyTransactionWhenCalledWithoutOne() {
        priceListingReader.listPrices(null, null);

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void listNightlyBillingRunsOpensReadOnlyTransactionWhenCalledWithoutOne() {
        billingRunReader.listNightlyBillingRuns(null, null);

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void listSimulatedEmailsOpensReadOnlyTransactionWhenCalledWithoutOne() {
        simulatedEmailListingReader.listSimulatedEmails(0, 10);

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void readFinancialDashboardOpensReadOnlyTransactionWhenCalledWithoutOne() {
        financialDashboardReader.readFinancialDashboard();

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void listDebtorsOpensReadOnlyTransactionWhenCalledWithoutOne() {
        debtorListingReader.listDebtors("DEBT_AGE");

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void readOwnCourseEnrollmentBillingStatusOpensReadOnlyTransactionWhenCalledWithoutOne() {
        assertThatThrownBy(() -> ownCourseEnrollmentBillingStatusReader.readOwnCourseEnrollmentBillingStatus(-1L, -1L)).isInstanceOf(RuntimeException.class);

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void readOwnProgramEnrollmentBillingStatusOpensReadOnlyTransactionWhenCalledWithoutOne() {
        assertThatThrownBy(() -> ownProgramEnrollmentBillingStatusReader.readOwnProgramEnrollmentBillingStatus(-1L, -1L)).isInstanceOf(RuntimeException.class);

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    @Test
    void readStudentBillingOverviewOpensReadOnlyTransactionWhenCalledWithoutOne() {
        assertThatThrownBy(() -> studentBillingOverviewReader.readStudentBillingOverview(-1L)).isInstanceOf(RuntimeException.class);

        assertThat(requestedDefinitions()).anyMatch(TransactionDefinition::isReadOnly);
    }

    private List<TransactionDefinition> requestedDefinitions() {
        ArgumentCaptor<TransactionDefinition> captor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, atLeastOnce()).getTransaction(captor.capture());
        return captor.getAllValues();
    }
}

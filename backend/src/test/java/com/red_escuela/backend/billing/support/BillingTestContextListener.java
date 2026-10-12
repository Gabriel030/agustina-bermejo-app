package com.red_escuela.backend.billing.support;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class BillingTestContextListener extends AbstractTestExecutionListener {

    @Override
    public void prepareTestInstance(TestContext testContext) {
        BillingTestApplicationContext.useContextOfTheRunningTest(testContext.getApplicationContext());
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        BillingTestApplicationContext.useContextOfTheRunningTest(testContext.getApplicationContext());
    }
}

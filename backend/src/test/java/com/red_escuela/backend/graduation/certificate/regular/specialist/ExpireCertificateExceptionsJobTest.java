package com.red_escuela.backend.graduation.certificate.regular.specialist;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExpireCertificateExceptionsJobTest {

    private final CertificateExceptionExpirer expirer = mock(CertificateExceptionExpirer.class);
    private final ExpireCertificateExceptionsJob job = new ExpireCertificateExceptionsJob(expirer);

    @Test
    void jobDelegatesTheNightlyRunToTheExpirer() {
        job.expireDueExceptions();

        verify(expirer).expireDue();
    }
}

package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.invoice.service.PendingInvoiceQueryService;
import com.red_escuela.backend.billing.service.MonthlyInstallmentIssuanceJob;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MockitoSpyBean(types = {
        MonthlyInstallmentIssuanceJob.class,
        ProgramEnrollmentService.class,
        CurrentAccountAllocationService.class,
        PendingInvoiceQueryService.class,
        LateFeeChargeRepository.class,
        EnrollmentWithdrawalBillingPort.class
}, reset = MockReset.AFTER)
public @interface SharedSpyBeans {
}

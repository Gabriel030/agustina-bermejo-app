package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.billing.account.usecase.AllocateAccountCreditUseCase;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.collections.service.LateFeeOnOverdueInvoiceListener;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.shared.mail.EmailService;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(RecordingProbeListenerConfiguration.class)
@MockitoBean(types = {EmailService.class, GoogleIdTokenService.class, AllocateAccountCreditUseCase.class, LateFeeAccrualStep.class, LateFeeOnOverdueInvoiceListener.class}, reset = MockReset.AFTER)
public @interface SharedMockBeans {
}

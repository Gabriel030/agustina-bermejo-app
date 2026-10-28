package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestRegistrar;
import com.red_escuela.backend.billing.annualupfront.service.RegisteredAnnualUpfrontRequest;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestAnnualUpfrontService implements RequestAnnualUpfrontUseCase {
    private final AnnualUpfrontRequestRegistrar registrar;

    public RequestAnnualUpfrontService(AnnualUpfrontRequestRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    @Transactional
    public RegisteredAnnualUpfrontRequest requestAnnualUpfrontForCurrentStudent(Long programEnrollmentId, String acceptedTermsVersion) {
        return registrar.registerRequestOfCurrentStudent(programEnrollmentId, acceptedTermsVersion);
    }
}

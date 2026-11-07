package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsView;

public interface PublishAnnualUpfrontTermsUseCase {

    AnnualUpfrontTermsView publishNewAnnualUpfrontTermsVersion(String text);
}

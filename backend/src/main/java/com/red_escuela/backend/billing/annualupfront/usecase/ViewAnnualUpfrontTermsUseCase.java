package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsView;

import java.util.List;

public interface ViewAnnualUpfrontTermsUseCase {

    AnnualUpfrontTermsView viewCurrentAnnualUpfrontTerms();

    AnnualUpfrontTermsView viewAnnualUpfrontTermsOfVersion(int versionNumber);

    List<AnnualUpfrontTermsView> listAnnualUpfrontTermsVersions();
}

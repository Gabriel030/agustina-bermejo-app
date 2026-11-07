package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsReader;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontTermsView;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ViewAnnualUpfrontTermsService implements ViewAnnualUpfrontTermsUseCase {
    private final AnnualUpfrontTermsReader reader;

    public ViewAnnualUpfrontTermsService(AnnualUpfrontTermsReader reader) {
        this.reader = reader;
    }

    @Override
    @PreAuthorize("hasAnyRole('ALUMNO', 'ADMINISTRATIVO', 'DIRECTOR')")
    public AnnualUpfrontTermsView viewCurrentAnnualUpfrontTerms() {
        return reader.findCurrentTerms();
    }

    @Override
    @PreAuthorize("hasAnyRole('ALUMNO', 'ADMINISTRATIVO', 'DIRECTOR')")
    public AnnualUpfrontTermsView viewAnnualUpfrontTermsOfVersion(int versionNumber) {
        return reader.findTermsOfVersion(versionNumber);
    }

    @Override
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<AnnualUpfrontTermsView> listAnnualUpfrontTermsVersions() {
        return reader.listTermsVersions();
    }
}

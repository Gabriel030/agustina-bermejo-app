package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestPage;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestReader;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestSearch;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestView;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ViewAnnualUpfrontRequestService implements ViewAnnualUpfrontRequestUseCase {
    private final AnnualUpfrontRequestReader reader;

    public ViewAnnualUpfrontRequestService(AnnualUpfrontRequestReader reader) {
        this.reader = reader;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public AnnualUpfrontRequestView viewAnnualUpfrontRequestOfCurrentStudent(Long requestId) {
        return reader.findRequestOfCurrentStudent(requestId);
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public List<AnnualUpfrontRequestView> listAnnualUpfrontRequestsOfCurrentStudent() {
        return reader.listRequestsOfCurrentStudent();
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public List<AnnualUpfrontRequestView> listAnnualUpfrontRequestsOfEnrollmentOfCurrentStudent(Long programEnrollmentId) {
        return reader.listRequestsOfEnrollmentOfCurrentStudent(programEnrollmentId);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public AnnualUpfrontRequestView viewAnnualUpfrontRequestForStaff(Long requestId) {
        return reader.findRequestForStaff(requestId);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<AnnualUpfrontRequestView> listAnnualUpfrontRequestsOfEnrollmentForStaff(Long programEnrollmentId) {
        return reader.listRequestsOfEnrollmentForStaff(programEnrollmentId);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public AnnualUpfrontRequestPage searchAnnualUpfrontRequestsForStaff(AnnualUpfrontRequestSearch search, int page, int size) {
        return reader.searchRequests(search, page, size);
    }
}

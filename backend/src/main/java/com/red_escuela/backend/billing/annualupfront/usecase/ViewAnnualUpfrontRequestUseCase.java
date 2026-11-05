package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestPage;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestSearch;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestView;

import java.util.List;

public interface ViewAnnualUpfrontRequestUseCase {

    AnnualUpfrontRequestView viewAnnualUpfrontRequestOfCurrentStudent(Long requestId);

    List<AnnualUpfrontRequestView> listAnnualUpfrontRequestsOfCurrentStudent();

    List<AnnualUpfrontRequestView> listAnnualUpfrontRequestsOfEnrollmentOfCurrentStudent(Long programEnrollmentId);

    AnnualUpfrontRequestView viewAnnualUpfrontRequestForStaff(Long requestId);

    List<AnnualUpfrontRequestView> listAnnualUpfrontRequestsOfEnrollmentForStaff(Long programEnrollmentId);

    AnnualUpfrontRequestPage searchAnnualUpfrontRequestsForStaff(AnnualUpfrontRequestSearch search, int page, int size);
}

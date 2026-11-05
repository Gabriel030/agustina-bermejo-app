package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus;
import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontRequestDetailResponse;
import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontRequestPageResponse;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestSearch;
import com.red_escuela.backend.billing.annualupfront.usecase.ViewAnnualUpfrontRequestUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class AnnualUpfrontRequestViewController {
    private final ViewAnnualUpfrontRequestUseCase viewUseCase;

    public AnnualUpfrontRequestViewController(ViewAnnualUpfrontRequestUseCase viewUseCase) {
        this.viewUseCase = viewUseCase;
    }

    @GetMapping("/me/annual-upfront-requests")
    public List<AnnualUpfrontRequestDetailResponse> listRequestsOfCurrentStudent() {
        return viewUseCase.listAnnualUpfrontRequestsOfCurrentStudent().stream().map(AnnualUpfrontRequestDetailResponse::from).toList();
    }

    @GetMapping("/me/annual-upfront-requests/{id}")
    public AnnualUpfrontRequestDetailResponse viewRequestOfCurrentStudent(@PathVariable Long id) {
        return AnnualUpfrontRequestDetailResponse.from(viewUseCase.viewAnnualUpfrontRequestOfCurrentStudent(id));
    }

    @GetMapping("/me/program-enrollments/{id}/annual-upfront-requests")
    public List<AnnualUpfrontRequestDetailResponse> listRequestsOfEnrollmentOfCurrentStudent(@PathVariable Long id) {
        return viewUseCase.listAnnualUpfrontRequestsOfEnrollmentOfCurrentStudent(id).stream().map(AnnualUpfrontRequestDetailResponse::from).toList();
    }

    @GetMapping("/annual-upfront-requests/{id}")
    public AnnualUpfrontRequestDetailResponse viewRequestForStaff(@PathVariable Long id) {
        return AnnualUpfrontRequestDetailResponse.from(viewUseCase.viewAnnualUpfrontRequestForStaff(id));
    }

    @GetMapping("/program-enrollments/{id}/annual-upfront-requests")
    public List<AnnualUpfrontRequestDetailResponse> listRequestsOfEnrollmentForStaff(@PathVariable Long id) {
        return viewUseCase.listAnnualUpfrontRequestsOfEnrollmentForStaff(id).stream().map(AnnualUpfrontRequestDetailResponse::from).toList();
    }

    @GetMapping("/annual-upfront-requests")
    public AnnualUpfrontRequestPageResponse searchRequestsForStaff(@RequestParam(required = false) AnnualUpfrontRequestStatus status, @RequestParam(required = false) Integer lectiveYear,
                                                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return AnnualUpfrontRequestPageResponse.from(viewUseCase.searchAnnualUpfrontRequestsForStaff(new AnnualUpfrontRequestSearch(status, lectiveYear), page, size));
    }
}

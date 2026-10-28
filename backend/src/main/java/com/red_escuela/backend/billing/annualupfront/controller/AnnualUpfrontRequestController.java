package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontRequestResponse;
import com.red_escuela.backend.billing.annualupfront.dto.RequestAnnualUpfrontBody;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontTermsNotAcceptedException;
import com.red_escuela.backend.billing.annualupfront.usecase.RequestAnnualUpfrontUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnualUpfrontRequestController {
    private final RequestAnnualUpfrontUseCase requestUseCase;

    public AnnualUpfrontRequestController(RequestAnnualUpfrontUseCase requestUseCase) {
        this.requestUseCase = requestUseCase;
    }

    @PostMapping("/me/program-enrollments/{id}/annual-upfront-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnualUpfrontRequestResponse requestAnnualUpfront(@PathVariable Long id, @RequestBody RequestAnnualUpfrontBody body) {
        if (!body.termsAccepted()) {
            throw new AnnualUpfrontTermsNotAcceptedException();
        }
        return AnnualUpfrontRequestResponse.from(requestUseCase.requestAnnualUpfrontForCurrentStudent(id, body.termsVersion()));
    }
}

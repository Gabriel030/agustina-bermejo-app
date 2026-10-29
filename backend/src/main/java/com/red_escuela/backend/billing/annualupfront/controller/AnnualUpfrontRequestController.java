package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontRequestResponse;
import com.red_escuela.backend.billing.annualupfront.dto.DiscardedAnnualUpfrontRequestResponse;
import com.red_escuela.backend.billing.annualupfront.dto.RequestAnnualUpfrontBody;
import com.red_escuela.backend.billing.annualupfront.usecase.DiscardAnnualUpfrontRequestUseCase;
import com.red_escuela.backend.billing.annualupfront.usecase.RequestAnnualUpfrontUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnualUpfrontRequestController {
    private final RequestAnnualUpfrontUseCase requestUseCase;

    private final DiscardAnnualUpfrontRequestUseCase discardUseCase;

    public AnnualUpfrontRequestController(RequestAnnualUpfrontUseCase requestUseCase, DiscardAnnualUpfrontRequestUseCase discardUseCase) {
        this.requestUseCase = requestUseCase;
        this.discardUseCase = discardUseCase;
    }

    @PostMapping("/me/annual-upfront-requests/{id}/discard")
    @PreAuthorize("hasRole('ALUMNO')")
    public DiscardedAnnualUpfrontRequestResponse discardAnnualUpfrontRequest(@PathVariable Long id) {
        return DiscardedAnnualUpfrontRequestResponse.from(discardUseCase.discardAnnualUpfrontRequestOfCurrentStudent(id));
    }

    @PostMapping("/me/program-enrollments/{id}/annual-upfront-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ALUMNO')")
    public AnnualUpfrontRequestResponse requestAnnualUpfront(@PathVariable Long id, @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
                                                             @RequestBody RequestAnnualUpfrontBody body) {
        return AnnualUpfrontRequestResponse.from(requestUseCase.requestAnnualUpfrontForCurrentStudent(id, body.termsAccepted(), body.termsVersion(), idempotencyKey));
    }
}

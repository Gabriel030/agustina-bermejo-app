package com.red_escuela.backend.graduation.certificate.approvedsubjects.controller;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase.RequestApprovedSubjectsCertificateUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class MeApprovedSubjectsCertificateController {

    private final RequestApprovedSubjectsCertificateUseCase requestUseCase;

    public MeApprovedSubjectsCertificateController(RequestApprovedSubjectsCertificateUseCase requestUseCase) {
        this.requestUseCase = requestUseCase;
    }

    @PostMapping("/me/approved-subjects-certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovedSubjectsCertificateResponse request(@Valid @RequestBody RequestApprovedSubjectsCertificateRequest request) {
        return requestUseCase.request(request);
    }
}

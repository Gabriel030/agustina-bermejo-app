package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.DecideCertificateExceptionRequest;
import com.red_escuela.backend.graduation.certificate.regular.dto.RequestCertificateExceptionRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ApproveCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.CreateDirectCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetCertificateExceptionSummaryUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListCertificateExceptionsUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RejectCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RequestCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.WithdrawCertificateExceptionUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/certificate-exceptions")
public class CertificateExceptionController {

    private static final String DIRECTOR_AUTHORITY = "ROLE_DIRECTOR";

    private final RequestCertificateExceptionUseCase requestUseCase;
    private final ApproveCertificateExceptionUseCase approveUseCase;
    private final RejectCertificateExceptionUseCase rejectUseCase;
    private final WithdrawCertificateExceptionUseCase withdrawUseCase;
    private final ListCertificateExceptionsUseCase listUseCase;
    private final GetCertificateExceptionUseCase getUseCase;
    private final CreateDirectCertificateExceptionUseCase createDirectUseCase;
    private final GetCertificateExceptionSummaryUseCase summaryUseCase;

    public CertificateExceptionController(RequestCertificateExceptionUseCase requestUseCase,
                                          ApproveCertificateExceptionUseCase approveUseCase,
                                          RejectCertificateExceptionUseCase rejectUseCase,
                                          WithdrawCertificateExceptionUseCase withdrawUseCase,
                                          ListCertificateExceptionsUseCase listUseCase, GetCertificateExceptionUseCase getUseCase,
                                          CreateDirectCertificateExceptionUseCase createDirectUseCase,
                                          GetCertificateExceptionSummaryUseCase summaryUseCase) {
        this.requestUseCase = requestUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.createDirectUseCase = createDirectUseCase;
        this.summaryUseCase = summaryUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "certificate.exceptions_request", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CertificateExceptionResponse request(@Valid @RequestBody RequestCertificateExceptionRequest request) {
        return requestUseCase.request(request);
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "certificate.exceptions_create_direct", legacyRoles = "ROLE_DIRECTOR")
    public CertificateExceptionResponse createDirect(@Valid @RequestBody RequestCertificateExceptionRequest request) {
        return createDirectUseCase.create(request);
    }

    @GetMapping
    @RequiresPermission(code = "certificate.exceptions_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<CertificateExceptionResponse> list(Authentication authentication, @RequestParam(required = false) Long studentId,
                                                   @RequestParam(required = false) CertificateExceptionStatus status) {
        return listUseCase.list(isDirector(authentication), studentId, status);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public CertificateExceptionSummaryResponse summary(Authentication authentication) {
        return summaryUseCase.summary(isDirector(authentication));
    }

    @GetMapping("/{id}")
    @RequiresPermission(code = "certificate.exceptions_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CertificateExceptionResponse get(Authentication authentication, @PathVariable Long id) {
        return getUseCase.get(id, isDirector(authentication));
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission(code = "certificate.exceptions_approve", legacyRoles = "ROLE_DIRECTOR")
    public CertificateExceptionResponse approve(@PathVariable Long id, @Valid @RequestBody DecideCertificateExceptionRequest request) {
        return approveUseCase.approve(id, request.reason());
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission(code = "certificate.exceptions_reject", legacyRoles = "ROLE_DIRECTOR")
    public CertificateExceptionResponse reject(@PathVariable Long id, @Valid @RequestBody DecideCertificateExceptionRequest request) {
        return rejectUseCase.reject(id, request.reason());
    }

    @PostMapping("/{id}/withdraw")
    @RequiresPermission(code = "certificate.exceptions_withdraw", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CertificateExceptionResponse withdraw(@PathVariable Long id) {
        return withdrawUseCase.withdraw(id);
    }

    private boolean isDirector(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> DIRECTOR_AUTHORITY.equals(authority.getAuthority()));
    }
}

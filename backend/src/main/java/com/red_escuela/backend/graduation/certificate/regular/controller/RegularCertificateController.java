package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.IssueRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RevokeRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.usecase.CheckRegularityUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetRegularCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetRegularCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.IssueRegularCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificatesUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RevokeRegularCertificateUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificatePage;

import java.util.List;

@RestController
@RequestMapping("/regular-certificates")
public class RegularCertificateController {

    private final CheckRegularityUseCase checkRegularityUseCase;
    private final IssueRegularCertificateUseCase issueUseCase;
    private final GetRegularCertificateUseCase getUseCase;
    private final ListRegularCertificatesUseCase listUseCase;
    private final GetRegularCertificatePdfUseCase pdfUseCase;
    private final RevokeRegularCertificateUseCase revokeUseCase;

    public RegularCertificateController(CheckRegularityUseCase checkRegularityUseCase,
                                        IssueRegularCertificateUseCase issueUseCase,
                                        GetRegularCertificateUseCase getUseCase,
                                        ListRegularCertificatesUseCase listUseCase,
                                        GetRegularCertificatePdfUseCase pdfUseCase,
                                        RevokeRegularCertificateUseCase revokeUseCase) {
        this.checkRegularityUseCase = checkRegularityUseCase;
        this.issueUseCase = issueUseCase;
        this.getUseCase = getUseCase;
        this.listUseCase = listUseCase;
        this.pdfUseCase = pdfUseCase;
        this.revokeUseCase = revokeUseCase;
    }

    @GetMapping("/eligibility")
    @RequiresPermission(code = "certificate.regular_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public RegularityResponse eligibility(@RequestParam RegularityKind kind, @RequestParam Long enrollmentId) {
        return checkRegularityUseCase.check(kind, enrollmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "certificate.issue", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public RegularCertificateResponse issue(@Valid @RequestBody IssueRegularCertificateRequest request) {
        return issueUseCase.issue(request);
    }

    @ApiResponse(responseCode = "200", description = "Constancias de regularidad, la más nueva primero; el total sin paginar viaja en el header X-Total-Count",
            headers = @Header(name = "X-Total-Count", description = "Cantidad total que cumple el filtro, sin paginar", schema = @Schema(type = "integer", format = "int64")))
    @GetMapping
    @RequiresPermission(code = "certificate.regular_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ResponseEntity<List<RegularCertificateResponse>> list(@RequestParam(required = false) Long studentId,
                                                                 @RequestParam(required = false) RegularCertificateStatus status,
                                                                 @RequestParam(required = false) Integer page,
                                                                 @RequestParam(required = false) Integer size) {
        CertificatePage<RegularCertificateResponse> certificates = listUseCase.listPage(studentId, status, page, size);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(certificates.totalElements())).body(certificates.items());
    }

    @GetMapping("/{id}")
    @RequiresPermission(code = "certificate.regular_view", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public RegularCertificateResponse get(@PathVariable Long id) {
        return getUseCase.get(id);
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission(code = "certificate.revoke", legacyRoles = "ROLE_DIRECTOR")
    public RegularCertificateResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeRegularCertificateRequest request) {
        return revokeUseCase.revoke(id, request.reason());
    }

    @GetMapping("/{id}/pdf")
    @RequiresPermission(code = "certificate.download", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        RegularCertificatePdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-regular-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }
}

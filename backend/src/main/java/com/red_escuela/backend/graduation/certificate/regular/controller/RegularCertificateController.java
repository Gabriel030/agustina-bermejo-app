package com.red_escuela.backend.graduation.certificate.regular.controller;

import com.red_escuela.backend.graduation.certificate.regular.dto.IssueRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.usecase.CheckRegularityUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetRegularCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetRegularCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.IssueRegularCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificatesUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/regular-certificates")
public class RegularCertificateController {

    private final CheckRegularityUseCase checkRegularityUseCase;
    private final IssueRegularCertificateUseCase issueUseCase;
    private final GetRegularCertificateUseCase getUseCase;
    private final ListRegularCertificatesUseCase listUseCase;
    private final GetRegularCertificatePdfUseCase pdfUseCase;

    public RegularCertificateController(CheckRegularityUseCase checkRegularityUseCase,
                                        IssueRegularCertificateUseCase issueUseCase,
                                        GetRegularCertificateUseCase getUseCase,
                                        ListRegularCertificatesUseCase listUseCase,
                                        GetRegularCertificatePdfUseCase pdfUseCase) {
        this.checkRegularityUseCase = checkRegularityUseCase;
        this.issueUseCase = issueUseCase;
        this.getUseCase = getUseCase;
        this.listUseCase = listUseCase;
        this.pdfUseCase = pdfUseCase;
    }

    @GetMapping("/eligibility")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public RegularityResponse eligibility(@RequestParam RegularityKind kind, @RequestParam Long enrollmentId) {
        return checkRegularityUseCase.check(kind, enrollmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public RegularCertificateResponse issue(@Valid @RequestBody IssueRegularCertificateRequest request) {
        return issueUseCase.issue(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public List<RegularCertificateResponse> list(@RequestParam(required = false) Long studentId,
                                                 @RequestParam(required = false) RegularCertificateStatus status) {
        return listUseCase.list(studentId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public RegularCertificateResponse get(@PathVariable Long id) {
        return getUseCase.get(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        RegularCertificatePdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-regular-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }
}

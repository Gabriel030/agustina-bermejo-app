package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduateResponse;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduatesFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateSummaryResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.dto.ReissueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.dto.RevokeGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.usecase.GetGraduationCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.GetGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.IssueGraduationCertificateBatchUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.IssueGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.ListEligibleGraduatesUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.ListGraduationCertificatesUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.PreviewGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.ReissueGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.RevokeGraduationCertificateUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

@RestController
@RequestMapping("/graduation-certificates")
public class GraduationCertificateController {

    private final IssueGraduationCertificateUseCase issueUseCase;
    private final GetGraduationCertificatePdfUseCase pdfUseCase;
    private final ListEligibleGraduatesUseCase eligibleUseCase;
    private final ListGraduationCertificatesUseCase listUseCase;
    private final GetGraduationCertificateUseCase getUseCase;
    private final PreviewGraduationCertificateUseCase previewUseCase;
    private final IssueGraduationCertificateBatchUseCase batchUseCase;
    private final RevokeGraduationCertificateUseCase revokeUseCase;
    private final ReissueGraduationCertificateUseCase reissueUseCase;

    public GraduationCertificateController(IssueGraduationCertificateUseCase issueUseCase,
                                           GetGraduationCertificatePdfUseCase pdfUseCase,
                                           ListEligibleGraduatesUseCase eligibleUseCase,
                                           ListGraduationCertificatesUseCase listUseCase,
                                           GetGraduationCertificateUseCase getUseCase,
                                           PreviewGraduationCertificateUseCase previewUseCase,
                                           IssueGraduationCertificateBatchUseCase batchUseCase,
                                           RevokeGraduationCertificateUseCase revokeUseCase,
                                           ReissueGraduationCertificateUseCase reissueUseCase) {
        this.issueUseCase = issueUseCase;
        this.pdfUseCase = pdfUseCase;
        this.eligibleUseCase = eligibleUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.previewUseCase = previewUseCase;
        this.batchUseCase = batchUseCase;
        this.revokeUseCase = revokeUseCase;
        this.reissueUseCase = reissueUseCase;
    }

    @GetMapping("/eligible")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public Page<EligibleGraduateResponse> eligible(
            @RequestParam(required = false) GraduationCertificateKind kind,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate graduatedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate graduatedTo,
            @PageableDefault(size = 25) Pageable pageable) {
        return eligibleUseCase.list(new EligibleGraduatesFilter(kind, query, graduatedFrom, graduatedTo), pageable);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public Page<GraduationCertificateSummaryResponse> list(
            @RequestParam(required = false) GraduationCertificateKind kind,
            @RequestParam(required = false) GraduationCertificateStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedTo,
            @PageableDefault(size = 25) Pageable pageable) {
        return listUseCase.list(new GraduationCertificateFilter(kind, status, query, issuedFrom, issuedTo), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public GraduationCertificateDetailResponse get(@PathVariable Long id) {
        return getUseCase.get(id);
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public ResponseEntity<byte[]> preview(@Valid @RequestBody IssueGraduationCertificateRequest request) {
        GraduationCertificatePdf pdf = previewUseCase.preview(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("vista-previa-certificado-egreso.pdf").build().toString())
                .body(pdf.content());
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public GraduationCertificateBatchResponse issueBatch(@Valid @RequestBody IssueGraduationCertificateBatchRequest request) {
        return batchUseCase.issueBatch(request);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('DIRECTOR')")
    public GraduationCertificateDetailResponse revoke(@PathVariable Long id,
                                                      @Valid @RequestBody RevokeGraduationCertificateRequest request) {
        return revokeUseCase.revoke(id, request.reason());
    }

    @PostMapping("/{id}/reissue")
    @PreAuthorize("hasRole('DIRECTOR')")
    public GraduationCertificateDetailResponse reissue(@PathVariable Long id,
                                                       @Valid @RequestBody ReissueGraduationCertificateRequest request) {
        return reissueUseCase.reissue(id, request.reason());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public GraduationCertificateResponse issue(@Valid @RequestBody IssueGraduationCertificateRequest request) {
        return issueUseCase.issue(request);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("certificado-egreso-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }
}

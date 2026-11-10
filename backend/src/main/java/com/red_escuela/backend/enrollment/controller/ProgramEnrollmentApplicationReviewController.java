package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ProgramApplicationDocumentFile;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.service.ProgramApplicationReviewViews;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationApprovalCheckUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationDocumentUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ListResolvedProgramEnrollmentApplicationsUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgramEnrollmentApplicationReviewController {

    private final ListResolvedProgramEnrollmentApplicationsUseCase listResolvedUseCase;
    private final GetProgramEnrollmentApplicationUseCase getApplicationUseCase;
    private final GetProgramEnrollmentApplicationApprovalCheckUseCase approvalCheckUseCase;
    private final GetProgramEnrollmentApplicationDocumentUseCase documentUseCase;
    private final ProgramApplicationReviewViews reviewViews;

    public ProgramEnrollmentApplicationReviewController(ListResolvedProgramEnrollmentApplicationsUseCase listResolvedUseCase,
                                                        GetProgramEnrollmentApplicationUseCase getApplicationUseCase,
                                                        GetProgramEnrollmentApplicationApprovalCheckUseCase approvalCheckUseCase,
                                                        GetProgramEnrollmentApplicationDocumentUseCase documentUseCase,
                                                        ProgramApplicationReviewViews reviewViews) {
        this.listResolvedUseCase = listResolvedUseCase;
        this.getApplicationUseCase = getApplicationUseCase;
        this.approvalCheckUseCase = approvalCheckUseCase;
        this.documentUseCase = documentUseCase;
        this.reviewViews = reviewViews;
    }

    @GetMapping("/program-enrollment-applications/history")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationPage history(
            @RequestParam(required = false) ProgramEnrollmentApplicationStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return listResolvedUseCase.list(status, search, page, size);
    }

    @GetMapping("/program-enrollment-applications/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse get(@PathVariable Long id) {
        return reviewViews.viewOf(getApplicationUseCase.get(id));
    }

    @GetMapping("/program-enrollment-applications/{id}/approval-check")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationApprovalCheckResponse approvalCheck(@PathVariable Long id) {
        return approvalCheckUseCase.get(id);
    }

    @GetMapping("/program-enrollment-applications/{id}/documents/{documentId}/file")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ResponseEntity<byte[]> documentFile(@PathVariable Long id, @PathVariable Long documentId) {
        ProgramApplicationDocumentFile file = documentUseCase.get(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName()).build().toString())
                .body(file.content());
    }
}

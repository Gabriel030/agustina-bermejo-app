package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.LegalAcceptanceResponse;
import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;
import com.red_escuela.backend.identity.entity.LegalDocumentType;
import com.red_escuela.backend.identity.usecase.legal.AcceptLegalDocumentsUseCase;
import com.red_escuela.backend.identity.usecase.legal.GetCurrentLegalDocumentsUseCase;
import com.red_escuela.backend.identity.usecase.legal.GetPendingLegalAcceptancesUseCase;
import com.red_escuela.backend.identity.usecase.legal.ListLegalAcceptancesUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegalDocumentController {

    private final GetCurrentLegalDocumentsUseCase getCurrentLegalDocumentsUseCase;
    private final GetPendingLegalAcceptancesUseCase getPendingLegalAcceptancesUseCase;
    private final AcceptLegalDocumentsUseCase acceptLegalDocumentsUseCase;
    private final ListLegalAcceptancesUseCase listLegalAcceptancesUseCase;

    public LegalDocumentController(GetCurrentLegalDocumentsUseCase getCurrentLegalDocumentsUseCase,
                                   GetPendingLegalAcceptancesUseCase getPendingLegalAcceptancesUseCase,
                                   AcceptLegalDocumentsUseCase acceptLegalDocumentsUseCase,
                                   ListLegalAcceptancesUseCase listLegalAcceptancesUseCase) {
        this.getCurrentLegalDocumentsUseCase = getCurrentLegalDocumentsUseCase;
        this.getPendingLegalAcceptancesUseCase = getPendingLegalAcceptancesUseCase;
        this.acceptLegalDocumentsUseCase = acceptLegalDocumentsUseCase;
        this.listLegalAcceptancesUseCase = listLegalAcceptancesUseCase;
    }

    @GetMapping("/legal/documents/current")
    public LegalDocumentsResponse getCurrentDocuments() {
        return getCurrentLegalDocumentsUseCase.getCurrentDocuments();
    }

    @GetMapping("/me/legal-acceptances/pending")
    public LegalDocumentsResponse getPendingAcceptances() {
        return getPendingLegalAcceptancesUseCase.getPendingDocuments();
    }

    @PostMapping("/me/legal-acceptances")
    public LegalDocumentsResponse acceptCurrentDocuments(HttpServletRequest request) {
        return acceptLegalDocumentsUseCase.acceptCurrentDocuments(
                LegalAcceptanceEvidence.of(request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @GetMapping("/legal-acceptances")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public PageResponse<LegalAcceptanceResponse> listAcceptances(
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) LegalDocumentType documentType,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listLegalAcceptancesUseCase.listAcceptances(personId, documentType == null ? null : documentType.name(), version, page, size);
    }
}

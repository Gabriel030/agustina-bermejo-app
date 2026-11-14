package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.LegalAcceptanceResponse;
import com.red_escuela.backend.identity.dto.ConfirmGuardianAcceptanceRequest;
import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;
import com.red_escuela.backend.identity.dto.NewsletterPreferenceResponse;
import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;
import com.red_escuela.backend.identity.dto.UpdateNewsletterPreferenceRequest;
import com.red_escuela.backend.identity.infrastructure.LoginRateLimiter;
import com.red_escuela.backend.identity.exception.RateLimitExceededException;
import com.red_escuela.backend.identity.usecase.legal.ConfirmGuardianAcceptanceUseCase;
import com.red_escuela.backend.identity.usecase.legal.GetNewsletterPreferenceUseCase;
import com.red_escuela.backend.identity.usecase.legal.ResendGuardianConfirmationUseCase;
import com.red_escuela.backend.identity.usecase.legal.UpdateNewsletterPreferenceUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final ConfirmGuardianAcceptanceUseCase confirmGuardianAcceptanceUseCase;
    private final ResendGuardianConfirmationUseCase resendGuardianConfirmationUseCase;
    private final GetNewsletterPreferenceUseCase getNewsletterPreferenceUseCase;
    private final UpdateNewsletterPreferenceUseCase updateNewsletterPreferenceUseCase;
    private final LoginRateLimiter rateLimiter;

    public LegalDocumentController(GetCurrentLegalDocumentsUseCase getCurrentLegalDocumentsUseCase,
                                   GetPendingLegalAcceptancesUseCase getPendingLegalAcceptancesUseCase,
                                   AcceptLegalDocumentsUseCase acceptLegalDocumentsUseCase,
                                   ListLegalAcceptancesUseCase listLegalAcceptancesUseCase,
                                   ConfirmGuardianAcceptanceUseCase confirmGuardianAcceptanceUseCase,
                                   ResendGuardianConfirmationUseCase resendGuardianConfirmationUseCase,
                                   GetNewsletterPreferenceUseCase getNewsletterPreferenceUseCase,
                                   UpdateNewsletterPreferenceUseCase updateNewsletterPreferenceUseCase,
                                   LoginRateLimiter rateLimiter) {
        this.getCurrentLegalDocumentsUseCase = getCurrentLegalDocumentsUseCase;
        this.getPendingLegalAcceptancesUseCase = getPendingLegalAcceptancesUseCase;
        this.acceptLegalDocumentsUseCase = acceptLegalDocumentsUseCase;
        this.listLegalAcceptancesUseCase = listLegalAcceptancesUseCase;
        this.confirmGuardianAcceptanceUseCase = confirmGuardianAcceptanceUseCase;
        this.resendGuardianConfirmationUseCase = resendGuardianConfirmationUseCase;
        this.getNewsletterPreferenceUseCase = getNewsletterPreferenceUseCase;
        this.updateNewsletterPreferenceUseCase = updateNewsletterPreferenceUseCase;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/legal/documents/current")
    public LegalDocumentsResponse getCurrentDocuments() {
        return getCurrentLegalDocumentsUseCase.getCurrentDocuments();
    }

    @GetMapping("/me/legal-acceptances/pending")
    public PendingLegalAcceptancesResponse getPendingAcceptances() {
        return getPendingLegalAcceptancesUseCase.getPendingDocuments();
    }

    @PostMapping("/me/legal-acceptances")
    public PendingLegalAcceptancesResponse acceptCurrentDocuments(HttpServletRequest request) {
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

    @PostMapping("/auth/legal/guardian-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmGuardianAcceptance(@Valid @RequestBody ConfirmGuardianAcceptanceRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeLogin(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Demasiados intentos, esperá un momento antes de reintentar");
        }
        confirmGuardianAcceptanceUseCase.confirmGuardianAcceptance(request.token(),
                LegalAcceptanceEvidence.of(httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/me/legal-acceptances/guardian-confirmation/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendGuardianConfirmation() {
        resendGuardianConfirmationUseCase.resendGuardianConfirmation();
    }

    @GetMapping("/me/newsletter-preference")
    public NewsletterPreferenceResponse getNewsletterPreference() {
        return getNewsletterPreferenceUseCase.getNewsletterPreference();
    }

    @PutMapping("/me/newsletter-preference")
    public NewsletterPreferenceResponse updateNewsletterPreference(@Valid @RequestBody UpdateNewsletterPreferenceRequest request) {
        return updateNewsletterPreferenceUseCase.updateNewsletterPreference(request.optedIn());
    }
}

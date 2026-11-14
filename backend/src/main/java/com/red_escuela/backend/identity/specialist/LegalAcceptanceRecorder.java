package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.LegalDocumentResponse;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.LegalAcceptanceRequiredException;
import com.red_escuela.backend.identity.repository.LegalAcceptanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LegalAcceptanceRecorder {

    private final LegalAcceptanceRepository legalAcceptanceRepository;
    private final LegalDocumentCatalog legalDocumentCatalog;
    private final Clock clock;
    private final CurrentPersonService currentPersonService;
    private final LegalAcceptanceEvidenceReader evidenceReader;

    public LegalAcceptanceRecorder(LegalAcceptanceRepository legalAcceptanceRepository,
                                   LegalDocumentCatalog legalDocumentCatalog, Clock clock,
                                   CurrentPersonService currentPersonService, LegalAcceptanceEvidenceReader evidenceReader) {
        this.legalAcceptanceRepository = legalAcceptanceRepository;
        this.legalDocumentCatalog = legalDocumentCatalog;
        this.clock = clock;
        this.currentPersonService = currentPersonService;
        this.evidenceReader = evidenceReader;
    }

    public void requireAcceptance(Boolean acceptedTerms, Boolean acceptedPrivacy) {
        if (!Boolean.TRUE.equals(acceptedTerms) || !Boolean.TRUE.equals(acceptedPrivacy)) {
            throw new LegalAcceptanceRequiredException();
        }
    }

    @Transactional
    public void acceptAtRegistration(Person person) {
        acceptCurrentDocuments(person.getId(), evidenceReader.readCurrent());
    }

    @Transactional
    public List<LegalDocumentResponse> acceptForCurrentPerson(LegalAcceptanceEvidence evidence) {
        Long personId = currentPersonService.resolveCurrentPerson().getId();
        acceptCurrentDocuments(personId, evidence);
        return pendingDocuments(personId);
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentResponse> pendingForCurrentPerson() {
        return pendingDocuments(currentPersonService.resolveCurrentPerson().getId());
    }

    @Transactional
    public void acceptCurrentDocuments(Long personId, LegalAcceptanceEvidence evidence) {
        LocalDateTime acceptedAt = LocalDateTime.now(clock);
        for (LegalDocumentResponse document : legalDocumentCatalog.current()) {
            legalAcceptanceRepository.insertIfAbsent(personId, document.type().name(), document.version(), acceptedAt,
                    evidence.ipHash(), evidence.userAgentSummary());
        }
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentResponse> pendingDocuments(Long personId) {
        return legalDocumentCatalog.current().stream()
                .filter(document -> !legalAcceptanceRepository.existsByPersonIdAndDocumentTypeAndVersion(
                        personId, document.type(), document.version()))
                .toList();
    }
}

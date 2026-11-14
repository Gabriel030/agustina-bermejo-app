package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.LegalDocumentResponse;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.GuardianAcceptanceRequiredException;
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
    private final LegalMinorChecker legalMinorChecker;

    public LegalAcceptanceRecorder(LegalAcceptanceRepository legalAcceptanceRepository,
                                   LegalDocumentCatalog legalDocumentCatalog, Clock clock,
                                   CurrentPersonService currentPersonService, LegalMinorChecker legalMinorChecker) {
        this.legalAcceptanceRepository = legalAcceptanceRepository;
        this.legalDocumentCatalog = legalDocumentCatalog;
        this.clock = clock;
        this.currentPersonService = currentPersonService;
        this.legalMinorChecker = legalMinorChecker;
    }

    @Transactional
    public void acceptForCurrentPerson(LegalAcceptanceEvidence evidence) {
        Person person = currentPersonService.resolveCurrentPerson();
        if (legalMinorChecker.isMinor(person)) {
            throw new GuardianAcceptanceRequiredException();
        }
        acceptCurrentDocuments(person.getId(), evidence);
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

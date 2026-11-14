package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalDocumentResponse;
import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.LegalDocumentPublicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LegalPendingFinder {

    private final CurrentPersonService currentPersonService;
    private final LegalAcceptanceRecorder legalAcceptanceRecorder;
    private final LegalDocumentPublicationRepository publicationRepository;
    private final LegalMinorChecker legalMinorChecker;
    private final Clock clock;
    private final long graceDays;

    public LegalPendingFinder(CurrentPersonService currentPersonService, LegalAcceptanceRecorder legalAcceptanceRecorder,
                              LegalDocumentPublicationRepository publicationRepository, LegalMinorChecker legalMinorChecker,
                              Clock clock, @Value("${app.legal.reacceptance-grace-days}") long graceDays) {
        this.currentPersonService = currentPersonService;
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
        this.publicationRepository = publicationRepository;
        this.legalMinorChecker = legalMinorChecker;
        this.clock = clock;
        this.graceDays = graceDays;
    }

    @Transactional
    public PendingLegalAcceptancesResponse pendingForCurrentPerson() {
        Person person = currentPersonService.resolveCurrentPerson();
        List<LegalDocumentResponse> pending = legalAcceptanceRecorder.pendingDocuments(person.getId());
        if (pending.isEmpty()) {
            return new PendingLegalAcceptancesResponse(List.of(), null, false, false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime earliest = null;
        for (LegalDocumentResponse document : pending) {
            publicationRepository.insertIfAbsent(document.type().name(), document.version(), now);
            LocalDateTime firstSeen = publicationRepository.findByDocumentTypeAndVersion(document.type(), document.version())
                    .orElseThrow().getFirstSeenAt();
            earliest = earliest == null || firstSeen.isBefore(earliest) ? firstSeen : earliest;
        }
        LocalDate deadline = earliest.toLocalDate().plusDays(graceDays);
        return new PendingLegalAcceptancesResponse(pending, deadline, LocalDate.now(clock).isAfter(deadline),
                legalMinorChecker.isMinor(person));
    }
}

package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.BirthDateRequiredException;
import com.red_escuela.backend.identity.exception.LegalAcceptanceRequiredException;
import com.red_escuela.backend.identity.exception.MinorGuardianRequiredException;
import com.red_escuela.backend.identity.repository.NewsletterPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class LegalRegistrationCompleter {

    private final LegalMinorChecker legalMinorChecker;
    private final LegalAcceptanceRecorder legalAcceptanceRecorder;
    private final LegalAcceptanceEvidenceReader evidenceReader;
    private final GuardianConfirmationIssuer guardianConfirmationIssuer;
    private final NewsletterPreferenceRepository newsletterPreferenceRepository;
    private final Clock clock;

    public LegalRegistrationCompleter(LegalMinorChecker legalMinorChecker, LegalAcceptanceRecorder legalAcceptanceRecorder,
                                      LegalAcceptanceEvidenceReader evidenceReader,
                                      GuardianConfirmationIssuer guardianConfirmationIssuer,
                                      NewsletterPreferenceRepository newsletterPreferenceRepository, Clock clock) {
        this.legalMinorChecker = legalMinorChecker;
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
        this.evidenceReader = evidenceReader;
        this.guardianConfirmationIssuer = guardianConfirmationIssuer;
        this.newsletterPreferenceRepository = newsletterPreferenceRepository;
        this.clock = clock;
    }

    public void requireValid(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.acceptedTerms()) || !Boolean.TRUE.equals(request.acceptedPrivacy())) {
            throw new LegalAcceptanceRequiredException();
        }
        if (request.birthDate() == null) {
            throw new BirthDateRequiredException();
        }
        if (legalMinorChecker.isMinor(request.birthDate()) && (isBlank(request.guardianName())
                || isBlank(request.guardianEmail()) || isBlank(request.guardianRelationship()))) {
            throw new MinorGuardianRequiredException();
        }
    }

    @Transactional
    public void complete(Person person, RegisterRequest request) {
        person.setBirthDate(request.birthDate());
        if (request.acceptedNewsletter() != null) {
            newsletterPreferenceRepository.upsert(person.getId(), request.acceptedNewsletter(), LocalDateTime.now(clock));
        }
        if (legalMinorChecker.isMinor(request.birthDate())) {
            guardianConfirmationIssuer.issue(person, request.guardianName(), request.guardianEmail(), request.guardianRelationship());
        } else {
            legalAcceptanceRecorder.acceptCurrentDocuments(person.getId(), evidenceReader.readCurrent());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.api.DebtExceptionConsumption;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DebtExceptionConsumer implements DebtExceptionConsumption {

    private final DocumentDebtExceptionRepository repository;
    private final DebtExceptionAuditRecorder auditRecorder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public DebtExceptionConsumer(DocumentDebtExceptionRepository repository, DebtExceptionAuditRecorder auditRecorder,
                                 CurrentActorFinder currentActorFinder, Clock clock) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<Long> consumeActive(Long studentId, CertificateDocumentType documentType, Long registrationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Long actorId = currentActorFinder.currentPersonId();
        for (Long id : candidates(studentId, documentType, registrationId, now)) {
            if (repository.markUsedIfActive(id, now, actorId) == 1) {
                auditRecorder.used(id, documentType.name(), studentId);
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    private List<Long> candidates(Long studentId, CertificateDocumentType documentType, Long registrationId, LocalDateTime now) {
        return registrationId == null
                ? repository.findActiveIdsWithoutRegistration(studentId, documentType, now)
                : repository.findActiveIdsFor(studentId, documentType, registrationId, now);
    }
}

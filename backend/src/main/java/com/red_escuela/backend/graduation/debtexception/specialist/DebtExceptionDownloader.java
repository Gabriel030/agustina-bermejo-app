package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.api.DebtExceptionDocument;
import com.red_escuela.backend.graduation.api.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.api.DebtExceptionScope;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionAlreadyUsedException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionExpiredException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DebtExceptionDownloader {

    private final DocumentDebtExceptionRepository repository;
    private final DebtExceptionDocumentIssuers issuers;
    private final DebtExceptionAuditRecorder auditRecorder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public DebtExceptionDownloader(DocumentDebtExceptionRepository repository, DebtExceptionDocumentIssuers issuers,
                                   DebtExceptionAuditRecorder auditRecorder, CurrentActorFinder currentActorFinder,
                                   Clock clock) {
        this.repository = repository;
        this.issuers = issuers;
        this.auditRecorder = auditRecorder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ProcedureDocumentPdf download(Long id) {
        DocumentDebtException exception = repository.findById(id).orElseThrow(DebtExceptionNotFoundException::new);
        DebtExceptionDocumentIssuer issuer = issuers.forType(exception.getDocumentType())
                .orElseThrow(DocumentOperationPendingException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        requireDownloadable(exception, now);
        if (repository.markUsedIfActive(id, now, currentActorFinder.currentPersonId()) != 1) {
            throw rejectionFor(repository.findStateById(id).orElseThrow(DebtExceptionNotFoundException::new).getStatus());
        }
        DebtExceptionDocument document = issuer.render(new DebtExceptionScope(exception.getId(), exception.getStudentId(),
                exception.getDocumentType(), exception.getEnrollmentId(), exception.getRegistrationId()));
        auditRecorder.used(id, exception.getDocumentType().name(), exception.getStudentId());
        auditRecorder.downloaded(id, exception.getDocumentType().name(), exception.getStudentId(), DebtExceptionAuditDetail.requireAllowed(document.auditDetail()));
        return new ProcedureDocumentPdf(document.code(), document.content());
    }

    private void requireDownloadable(DocumentDebtException exception, LocalDateTime now) {
        if (exception.getStatus() != DebtExceptionStatus.ACTIVE || !exception.getExpiresAt().isAfter(now)) {
            throw rejectionFor(exception.getStatus());
        }
    }

    private RuntimeException rejectionFor(DebtExceptionStatus status) {
        if (status == DebtExceptionStatus.USED) {
            return new DebtExceptionAlreadyUsedException();
        }
        return new DebtExceptionExpiredException();
    }
}

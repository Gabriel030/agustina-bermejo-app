package com.red_escuela.backend.graduation.debtexception.specialist;

import com.red_escuela.backend.graduation.debtexception.port.DebtExceptionScope;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.IssueDebtExceptionRequest;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentExistenceChecker;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DebtExceptionIssuer {

    private final StudentExistenceChecker studentExistenceChecker;
    private final CurrentActorFinder currentActorFinder;
    private final DocumentDebtExceptionRepository repository;
    private final DebtExceptionAuditRecorder auditRecorder;
    private final DebtExceptionResponses responses;
    private final DebtExceptionDocumentIssuers documentIssuers;
    private final Clock clock;

    public DebtExceptionIssuer(StudentExistenceChecker studentExistenceChecker, CurrentActorFinder currentActorFinder,
                               DocumentDebtExceptionRepository repository, DebtExceptionAuditRecorder auditRecorder,
                               DebtExceptionResponses responses, DebtExceptionDocumentIssuers documentIssuers, Clock clock) {
        this.studentExistenceChecker = studentExistenceChecker;
        this.currentActorFinder = currentActorFinder;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.responses = responses;
        this.documentIssuers = documentIssuers;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DebtExceptionResponse issue(IssueDebtExceptionRequest request) {
        studentExistenceChecker.requireExistingStudent(request.studentId());
        documentIssuers.forType(request.documentType()).ifPresent(issuer -> issuer.requireScopeBelongsToStudent(
                new DebtExceptionScope(null, request.studentId(), request.documentType(), request.enrollmentId(),
                        request.registrationId())));
        DocumentDebtException exception = repository.save(DocumentDebtException.issue(request.studentId(),
                request.documentType(), request.enrollmentId(), request.registrationId(), request.reason(),
                currentActorFinder.currentPersonId(), LocalDateTime.now(clock), request.validityDays()));
        auditRecorder.issued(exception);
        return responses.of(exception);
    }
}

package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestNotFoundException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationResumeOutcome;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.operation.AuthorizationRequestObservers;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Component
public class AuthorizationRequestDecider {
    private final AuthorizationRequestRepository repository;
    private final AuthorizationCallerAccess callerAccess;
    private final CurrentPersonService currentPersonService;
    private final AuthorizedOperationResumer resumer;
    private final TransactionTemplate decisionTransaction;
    private final TransactionTemplate resumeTransaction;
    private final AuthorizationRequestObservers observers;
    private final Clock clock;

    public AuthorizationRequestDecider(AuthorizationRequestRepository repository, AuthorizationCallerAccess callerAccess, CurrentPersonService currentPersonService,
                                       AuthorizedOperationResumer resumer, PlatformTransactionManager transactionManager, AuthorizationRequestObservers observers, Clock clock) {
        this.repository = repository;
        this.callerAccess = callerAccess;
        this.currentPersonService = currentPersonService;
        this.resumer = resumer;
        this.decisionTransaction = new TransactionTemplate(transactionManager);
        this.resumeTransaction = new TransactionTemplate(transactionManager);
        this.resumeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.observers = observers;
        this.clock = clock;
    }

    public AuthorizationRequest approveAndResume(Long id, String decisionReason) {
        decisionTransaction.executeWithoutResult(status -> approve(id, decisionReason));
        try {
            resumeTransaction.executeWithoutResult(status -> resumeApprovedOperation(id));
        } catch (BadRequestException | ConflictException rejection) {
            decisionTransaction.executeWithoutResult(status -> recordRejectedOnResume(id, rejection.getMessage()));
            throw rejection;
        }
        return findRequest(id);
    }

    public AuthorizationRequest reject(Long id, String decisionReason) {
        return decisionTransaction.execute(status -> {
            AuthorizationRequest request = findRequest(id);
            Person decider = currentPersonService.resolveCurrentPerson();
            callerAccess.requireMayDecide(request, decider);
            request.reject(decider, decisionReason, Instant.now(clock));
            observers.afterRejected(id, request.getOperationType(), decider.getId(), decisionReason);
            return repository.save(request);
        });
    }

    public AuthorizationRequest withdraw(Long id) {
        return decisionTransaction.execute(status -> {
            AuthorizationRequest request = findRequest(id);
            Person requester = currentPersonService.resolveCurrentPerson();
            callerAccess.requireIsRequester(request, requester);
            request.withdraw(requester, Instant.now(clock));
            observers.afterWithdrawn(id, request.getOperationType(), requester.getId());
            return repository.save(request);
        });
    }

    private void approve(Long id, String decisionReason) {
        AuthorizationRequest request = findRequest(id);
        Person decider = currentPersonService.resolveCurrentPerson();
        callerAccess.requireMayDecide(request, decider);
        request.approve(decider, decisionReason, Instant.now(clock));
        repository.save(request);
    }

    private void resumeApprovedOperation(Long id) {
        AuthorizationRequest request = findRequest(id);
        resumer.resumeApprovedOperation(request);
        repository.save(request);
    }

    private void recordRejectedOnResume(Long id, String rejectionMessage) {
        AuthorizationRequest request = findRequest(id);
        request.recordResumeOutcome(AuthorizationResumeOutcome.REJECTED_ON_RESUME);
        observers.afterResumeRejected(id, request.getOperationType(), request.getDecidedBy().getId(), rejectionMessage);
        repository.save(request);
    }

    private AuthorizationRequest findRequest(Long id) {
        return repository.findById(id).orElseThrow(() -> new AuthorizationRequestNotFoundException(id));
    }
}

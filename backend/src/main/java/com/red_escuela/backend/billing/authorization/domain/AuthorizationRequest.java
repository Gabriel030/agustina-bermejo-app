package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authorization_request")
public class AuthorizationRequest {
    private static final String EXPIRATION_PROCESS = "AuthorizationRequestExpiration";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private BillingOperationType operationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operation_payload", nullable = false, columnDefinition = "jsonb")
    private String operationPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role", nullable = false)
    private AuthorizationRole requiredRole;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuthorizationRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Person requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Person decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "resume_outcome")
    private AuthorizationResumeOutcome resumeOutcome;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "authorizationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<AuthorizationRequestStatusChange> statusChanges = new ArrayList<>();

    public static AuthorizationRequest open(BillingOperationType operationType, String operationPayload, AuthorizationRole requiredRole,
                                            String reason, Person requestedBy, LocalDate expiresOn, Instant now) {
        AuthorizationRequest request = new AuthorizationRequest();
        request.operationType = operationType;
        request.operationPayload = operationPayload;
        request.requiredRole = requiredRole;
        request.reason = reason;
        request.requestedBy = requestedBy;
        request.requestedAt = now;
        request.expiresOn = expiresOn;
        request.moveTo(AuthorizationRequestEvent.OPEN, StatusChangeActor.ofPerson(requestedBy.getId()), now, reason);
        return request;
    }

    public void approve(Person decider, String decisionReason, Instant now) {
        decide(AuthorizationRequestEvent.APPROVE, decider, decisionReason, now);
    }

    public void reject(Person decider, String decisionReason, Instant now) {
        decide(AuthorizationRequestEvent.REJECT, decider, decisionReason, now);
    }

    public void withdraw(Person requester, Instant now) {
        decide(AuthorizationRequestEvent.WITHDRAW, requester, null, now);
    }

    public void expire(Instant now) {
        moveTo(AuthorizationRequestEvent.EXPIRE, StatusChangeActor.ofProcess(EXPIRATION_PROCESS), now, null);
        this.decidedAt = now;
    }

    public void recordResumeOutcome(AuthorizationResumeOutcome outcome) {
        if (status != AuthorizationRequestStatus.APPROVED || resumeOutcome != null) {
            throw new IllegalStateException("Resume outcome can be recorded once, on an approved request");
        }
        this.resumeOutcome = outcome;
    }

    public boolean isPending() {
        return status == AuthorizationRequestStatus.PENDING;
    }

    public List<AuthorizationRequestStatusChange> getStatusChanges() {
        return List.copyOf(statusChanges);
    }

    private void decide(AuthorizationRequestEvent event, Person decider, String reason, Instant now) {
        moveTo(event, StatusChangeActor.ofPerson(decider.getId()), now, reason);
        this.decidedBy = decider;
        this.decidedAt = now;
        this.decisionReason = reason;
    }

    private void moveTo(AuthorizationRequestEvent event, StatusChangeActor actor, Instant now, String reason) {
        AuthorizationRequestStatus target = AuthorizationRequestStateMachine.findTargetStateFor(status, event)
                .orElseThrow(() -> new IllegalAuthorizationRequestTransitionException(status, event));
        statusChanges.add(new AuthorizationRequestStatusChange(this, status, target, event, actor, now, reason));
        this.status = target;
    }
}

package com.red_escuela.backend.graduation.certificate.regular.entity;

import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionDecisionReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionAlreadyConsumedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionNotApprovedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionTargetMismatchException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionWithdrawNotAllowedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OrganismKindRequiredException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OrganismReferenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OwnExceptionDecisionException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Entity
@Table(name = "certificate_exception_approval")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateExceptionApproval {

    public static final int REASON_MIN_LENGTH = 20;
    public static final int REASON_MAX_LENGTH = 1000;
    public static final int DECISION_REASON_MIN_LENGTH = 10;
    public static final int DECISION_REASON_MAX_LENGTH = 500;
    public static final int REFERENCE_MAX_LENGTH = 200;
    public static final String EXPIRATION_PROCESS = "ExpireCertificateExceptionsJob";
    public static final String DIRECTOR_CREATION_REASON = "Creada y aprobada por el Director";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private RegularityKind kind;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "organism_kind", nullable = false, length = 30, updatable = false)
    private OrganismKind organismKind;

    @Column(name = "organism_reference", nullable = false, length = 200, updatable = false)
    private String organismReference;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CertificateExceptionStatus status;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "created_by_director", nullable = false, updatable = false)
    private boolean createdByDirector;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "debt_total_at_request", precision = 12, scale = 2, updatable = false))
    @AttributeOverride(name = "currency", column = @Column(name = "debt_currency_at_request", length = 3, updatable = false))
    private Money debtAtRequest;

    @Column(name = "overdue_installments_at_request", updatable = false)
    private Integer overdueInstallmentsAtRequest;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "consumed_certificate_id")
    private Long consumedCertificateId;

    @OneToMany(mappedBy = "approval", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<CertificateExceptionStatusChange> statusChanges = new ArrayList<>();

    public static CertificateExceptionApproval request(RegularityKind kind, Long enrollmentId, Long studentId, OrganismKind organismKind,
                                                       String rawReference, String rawReason, Long requestedById, LocalDateTime now) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(enrollmentId);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(requestedById);
        Objects.requireNonNull(now);
        if (organismKind == null) {
            throw new OrganismKindRequiredException();
        }
        CertificateExceptionApproval approval = new CertificateExceptionApproval();
        approval.studentId = studentId;
        approval.kind = kind;
        approval.programEnrollmentId = kind == RegularityKind.PROGRAM ? enrollmentId : null;
        approval.courseEnrollmentId = kind == RegularityKind.COURSE ? enrollmentId : null;
        approval.organismKind = organismKind;
        approval.organismReference = boundedText(rawReference, 1, REFERENCE_MAX_LENGTH, OrganismReferenceInvalidException::new);
        approval.reason = boundedText(rawReason, REASON_MIN_LENGTH, REASON_MAX_LENGTH, CertificateExceptionReasonInvalidException::new);
        approval.requestedById = requestedById;
        approval.requestedAt = now;
        approval.moveTo(CertificateExceptionEvent.REQUEST, StatusChangeActor.ofPerson(requestedById), now, approval.reason);
        return approval;
    }

    public static CertificateExceptionApproval createApprovedByDirector(RegularityKind kind, Long enrollmentId, Long studentId,
                                                                        OrganismKind organismKind, String rawReference, String rawReason,
                                                                        Long directorId, ValidityDays validity, LocalDateTime now) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(enrollmentId);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(directorId);
        Objects.requireNonNull(validity);
        Objects.requireNonNull(now);
        if (organismKind == null) {
            throw new OrganismKindRequiredException();
        }
        CertificateExceptionApproval approval = new CertificateExceptionApproval();
        approval.studentId = studentId;
        approval.kind = kind;
        approval.programEnrollmentId = kind == RegularityKind.PROGRAM ? enrollmentId : null;
        approval.courseEnrollmentId = kind == RegularityKind.COURSE ? enrollmentId : null;
        approval.organismKind = organismKind;
        approval.organismReference = boundedText(rawReference, 1, REFERENCE_MAX_LENGTH, OrganismReferenceInvalidException::new);
        approval.reason = boundedText(rawReason, REASON_MIN_LENGTH, REASON_MAX_LENGTH, CertificateExceptionReasonInvalidException::new);
        approval.requestedById = directorId;
        approval.requestedAt = now;
        approval.createdByDirector = true;
        approval.moveTo(CertificateExceptionEvent.DIRECT_APPROVE, StatusChangeActor.ofPerson(directorId), now, DIRECTOR_CREATION_REASON);
        approval.decidedById = directorId;
        approval.decidedAt = now;
        approval.decisionReason = DIRECTOR_CREATION_REASON;
        approval.validUntil = now.plusDays(validity.value());
        return approval;
    }

    public void recordDebtAtRequest(Money totalDue, int overdueInstallments) {
        Objects.requireNonNull(totalDue);
        this.debtAtRequest = totalDue;
        this.overdueInstallmentsAtRequest = overdueInstallments;
    }

    public void approve(Long deciderId, String rawDecisionReason, ValidityDays validity, LocalDateTime now) {
        Objects.requireNonNull(validity);
        String decisionReason = requireDecisionAllowed(deciderId, rawDecisionReason);
        moveTo(CertificateExceptionEvent.APPROVE, StatusChangeActor.ofPerson(deciderId), now, decisionReason);
        this.decidedById = deciderId;
        this.decidedAt = now;
        this.decisionReason = decisionReason;
        this.validUntil = now.plusDays(validity.value());
    }

    public void reject(Long deciderId, String rawDecisionReason, LocalDateTime now) {
        String decisionReason = requireDecisionAllowed(deciderId, rawDecisionReason);
        moveTo(CertificateExceptionEvent.REJECT, StatusChangeActor.ofPerson(deciderId), now, decisionReason);
        this.decidedById = deciderId;
        this.decidedAt = now;
        this.decisionReason = decisionReason;
    }

    public void withdraw(Long actorId, LocalDateTime now) {
        if (!requestedById.equals(actorId)) {
            throw new ExceptionWithdrawNotAllowedException();
        }
        moveTo(CertificateExceptionEvent.WITHDRAW, StatusChangeActor.ofPerson(actorId), now, null);
    }

    public void consume(Long certificateId, Long expectedStudentId, RegularityKind expectedKind, Long expectedEnrollmentId, Long actorId,
                        LocalDateTime now) {
        Objects.requireNonNull(certificateId);
        requireUsableFor(expectedStudentId, expectedKind, expectedEnrollmentId, now);
        moveTo(CertificateExceptionEvent.CONSUME, StatusChangeActor.ofPerson(actorId), now, null);
        this.consumedAt = now;
        this.consumedCertificateId = certificateId;
    }

    public void requireUsableFor(Long expectedStudentId, RegularityKind expectedKind, Long expectedEnrollmentId, LocalDateTime now) {
        if (!studentId.equals(expectedStudentId) || kind != expectedKind || !enrollmentId().equals(expectedEnrollmentId)) {
            throw new ExceptionTargetMismatchException();
        }
        if (status == CertificateExceptionStatus.CONSUMED) {
            throw new ExceptionAlreadyConsumedException();
        }
        if (status != CertificateExceptionStatus.APPROVED || !isValidAt(now)) {
            throw new ExceptionNotApprovedException();
        }
    }

    public void expire(LocalDateTime now) {
        moveTo(CertificateExceptionEvent.EXPIRE, StatusChangeActor.ofProcess(EXPIRATION_PROCESS), now, null);
    }

    public boolean isValidAt(LocalDateTime moment) {
        return status == CertificateExceptionStatus.APPROVED && validUntil != null && moment.isBefore(validUntil);
    }

    public boolean isDueToExpireAt(LocalDateTime moment) {
        return status == CertificateExceptionStatus.APPROVED && validUntil != null && !moment.isBefore(validUntil);
    }

    public Long enrollmentId() {
        return kind == RegularityKind.PROGRAM ? programEnrollmentId : courseEnrollmentId;
    }

    private String requireDecisionAllowed(Long deciderId, String rawDecisionReason) {
        Objects.requireNonNull(deciderId);
        if (deciderId.equals(requestedById)) {
            throw new OwnExceptionDecisionException();
        }
        return boundedText(rawDecisionReason, DECISION_REASON_MIN_LENGTH, DECISION_REASON_MAX_LENGTH,
                CertificateExceptionDecisionReasonInvalidException::new);
    }

    private void moveTo(CertificateExceptionEvent event, StatusChangeActor actor, LocalDateTime now, String changeReason) {
        CertificateExceptionStatus target = CertificateExceptionStateMachine.requireTargetFor(status, event);
        statusChanges.add(new CertificateExceptionStatusChange(this, status, target, event, actor, now, changeReason));
        this.status = target;
    }

    private static String boundedText(String raw, int min, int max, Supplier<? extends RuntimeException> invalid) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < min || text.length() > max) {
            throw invalid.get();
        }
        return text;
    }
}

package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.persistence.MonetaryAdjustmentConverter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "student_discount")
public class StudentDiscount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "discount_definition_id", updatable = false)
    private Long discountDefinitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, updatable = false)
    private DiscountType discountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_invoice_type", nullable = false, updatable = false)
    private InvoiceType applicableInvoiceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false)
    private StudentDiscountOrigin origin;

    @Convert(converter = MonetaryAdjustmentConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "calculation", columnDefinition = "jsonb", nullable = false, updatable = false)
    private MonetaryAdjustment calculation;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "cap_per_invoice", updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "cap_per_invoice_currency", updatable = false))})
    private NonNegativeMoney capPerInvoice;

    @Column(name = "priority", nullable = false, updatable = false)
    private int priority;

    @Column(name = "from_period", nullable = false, updatable = false)
    private YearMonth fromPeriod;

    @Column(name = "to_period", nullable = false, updatable = false)
    private YearMonth toPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentDiscountStatus status;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by_id", nullable = false, updatable = false)
    private Long grantedById;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "authorization_request_id", updatable = false)
    private Long authorizationRequestId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "revoked_from_period")
    private YearMonth revokedFromPeriod;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "studentDiscount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<StudentDiscountStatusChange> statusChanges = new ArrayList<>();

    public static StudentDiscount grantStudentDiscount(StudentDiscountGrant grant, Long grantedById, Long authorizationRequestId, Instant now) {
        StudentDiscount discount = new StudentDiscount();
        discount.programEnrollmentId = grant.enrollment().kind() == EnrollmentKind.PROGRAM ? grant.enrollment().id() : null;
        discount.courseEnrollmentId = grant.enrollment().kind() == EnrollmentKind.COURSE ? grant.enrollment().id() : null;
        discount.studentId = grant.studentId();
        discount.currency = grant.currency();
        discount.discountDefinitionId = grant.discountDefinitionId();
        discount.discountType = grant.discountType();
        discount.applicableInvoiceType = grant.applicableInvoiceType();
        discount.origin = StudentDiscountOrigin.DIRECT_GRANT;
        discount.calculation = grant.calculation();
        discount.capPerInvoice = grant.capPerInvoice();
        discount.priority = grant.priority();
        discount.fromPeriod = grant.range().from();
        discount.toPeriod = grant.range().to();
        discount.grantedAt = now;
        discount.grantedById = grantedById;
        discount.reason = grant.reason();
        discount.authorizationRequestId = authorizationRequestId;
        discount.moveTo(StudentDiscountEvent.DISCOUNT_GRANTED, StatusChangeActor.ofPerson(grantedById), now, grant.reason());
        return discount;
    }

    public void revokeStudentDiscount(String reason, YearMonth nextInstallmentPeriod, StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.DISCOUNT_REVOKED, actor, now, reason);
        this.revokedAt = now;
        this.revokedById = actor.personId();
        this.revocationReason = reason;
        this.revokedFromPeriod = nextInstallmentPeriod;
    }

    public void endWithEnrollment(StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.ENROLLMENT_ENDED, actor, now, null);
    }

    public void recordLastInstallmentConsumed(StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.LAST_INSTALLMENT_CONSUMED, actor, now, null);
    }

    public void returnInstallmentConsumption(StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.CONSUMPTION_RETURNED, actor, now, null);
    }

    public ScholarshipPeriodRange periodRange() {
        return new ScholarshipPeriodRange(fromPeriod, toPeriod);
    }

    public boolean coversPeriod(YearMonth period) {
        boolean notRevokedBefore = revokedFromPeriod == null || period.isBefore(revokedFromPeriod);
        return periodRange().contains(period) && notRevokedBefore && status != StudentDiscountStatus.ENDED;
    }

    private void moveTo(StudentDiscountEvent event, StatusChangeActor actor, Instant now, String reason) {
        StudentDiscountStatus target = StudentDiscountStateMachine.requireTargetFor(status, event);
        statusChanges.add(new StudentDiscountStatusChange(this, status, target, event, actor, now, reason));
        this.status = target;
    }
}

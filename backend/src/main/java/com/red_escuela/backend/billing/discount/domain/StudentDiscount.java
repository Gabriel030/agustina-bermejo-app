package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.IllegalStudentDiscountTransitionException;
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
    private static final int ENTRY_DISCOUNT_PRIORITY = 1;
    private static final int SINGLE_USE_INSTALLMENTS = 1;

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

    @Column(name = "installments_total", updatable = false)
    private Integer installmentsTotal;

    @Column(name = "from_period", nullable = false, updatable = false)
    private YearMonth fromPeriod;

    @Column(name = "to_period", nullable = false, updatable = false)
    private YearMonth toPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentDiscountStatus status;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by_id", updatable = false)
    private Long grantedById;

    @Column(name = "configuration_version_id", updatable = false)
    private Long configurationVersionId;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "authorization_request_id", updatable = false)
    private Long authorizationRequestId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revoked_by_process")
    private String revokedByProcess;

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

    public static StudentDiscount acquireCohortPromotion(CohortPromotionAcquisition acquisition, Instant now) {
        DiscountDefinition definition = acquisition.definition();
        StudentDiscount discount = new StudentDiscount();
        discount.programEnrollmentId = acquisition.enrollment().kind() == EnrollmentKind.PROGRAM ? acquisition.enrollment().id() : null;
        discount.courseEnrollmentId = acquisition.enrollment().kind() == EnrollmentKind.COURSE ? acquisition.enrollment().id() : null;
        discount.studentId = acquisition.studentId();
        discount.currency = acquisition.currency();
        discount.discountDefinitionId = definition.getId();
        discount.discountType = DiscountType.COHORT_PROMOTION;
        discount.applicableInvoiceType = definition.getApplicableInvoiceType();
        discount.origin = StudentDiscountOrigin.COHORT_ACQUISITION;
        discount.calculation = definition.getCalculation();
        discount.capPerInvoice = definition.getCapPerInvoice();
        discount.priority = definition.getPriority();
        discount.installmentsTotal = definition.getInstallmentsTotal();
        discount.fromPeriod = acquisition.referenceRange().from();
        discount.toPeriod = acquisition.referenceRange().to();
        discount.grantedAt = now;
        discount.grantedById = acquisition.actorPersonId();
        discount.reason = acquisition.reason();
        discount.moveTo(StudentDiscountEvent.DISCOUNT_GRANTED, StatusChangeActor.ofPerson(acquisition.actorPersonId()), now, acquisition.reason());
        return discount;
    }

    public static StudentDiscount reserveAnnualUpfrontDiscount(AnnualUpfrontReservation reservation, Instant now) {
        StudentDiscount discount = new StudentDiscount();
        discount.programEnrollmentId = reservation.programEnrollmentId();
        discount.studentId = reservation.studentId();
        discount.currency = reservation.currency();
        discount.discountDefinitionId = reservation.definition().getId();
        discount.discountType = DiscountType.ANNUAL_UPFRONT;
        discount.applicableInvoiceType = InvoiceType.ANNUAL_UPFRONT;
        discount.origin = StudentDiscountOrigin.ANNUAL_UPFRONT_REQUEST;
        discount.calculation = reservation.definition().getCalculation();
        discount.capPerInvoice = reservation.definition().getCapPerInvoice();
        discount.priority = reservation.definition().getPriority();
        discount.fromPeriod = reservation.range().from();
        discount.toPeriod = reservation.range().to();
        discount.grantedAt = now;
        discount.grantedById = reservation.requestedByPersonId();
        discount.reason = reservation.reason();
        discount.moveTo(StudentDiscountEvent.DISCOUNT_GRANTED, StatusChangeActor.ofPerson(reservation.requestedByPersonId()), now, reservation.reason());
        return discount;
    }

    public static StudentDiscount registerCohortEntryDiscount(CohortEntryRegistration registration, Instant now) {
        StudentDiscount discount = new StudentDiscount();
        discount.programEnrollmentId = registration.enrollment().kind() == EnrollmentKind.PROGRAM ? registration.enrollment().id() : null;
        discount.courseEnrollmentId = registration.enrollment().kind() == EnrollmentKind.COURSE ? registration.enrollment().id() : null;
        discount.studentId = registration.studentId();
        discount.currency = registration.currency();
        discount.configurationVersionId = registration.configurationVersionId();
        discount.discountType = DiscountType.COHORT_ENTRY;
        discount.applicableInvoiceType = InvoiceType.ENROLLMENT_FEE;
        discount.origin = StudentDiscountOrigin.COHORT_ENTRY_CONFIGURATION;
        discount.calculation = new MonetaryAdjustment.Proportional(registration.discount());
        discount.priority = ENTRY_DISCOUNT_PRIORITY;
        discount.installmentsTotal = SINGLE_USE_INSTALLMENTS;
        discount.fromPeriod = registration.period();
        discount.toPeriod = registration.period();
        discount.grantedAt = now;
        discount.grantedById = registration.actor().personId();
        discount.reason = registration.reason();
        discount.moveTo(StudentDiscountEvent.DISCOUNT_GRANTED, registration.actor(), now, registration.reason());
        discount.moveTo(StudentDiscountEvent.LAST_INSTALLMENT_CONSUMED, registration.actor(), now, null);
        return discount;
    }

    public void revokeStudentDiscount(String reason, YearMonth nextInstallmentPeriod, StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.DISCOUNT_REVOKED, actor, now, reason);
        this.revokedAt = now;
        this.revokedById = actor.personId();
        this.revokedByProcess = actor.processName();
        this.revocationReason = reason;
        this.revokedFromPeriod = nextInstallmentPeriod;
    }

    public void endWithEnrollment(StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.ENROLLMENT_ENDED, actor, now, null);
    }

    public void recordLastInstallmentConsumed(StatusChangeActor actor, Instant now) {
        if (installmentsTotal == null) {
            throw new IllegalStudentDiscountTransitionException(status, StudentDiscountEvent.LAST_INSTALLMENT_CONSUMED);
        }
        moveTo(StudentDiscountEvent.LAST_INSTALLMENT_CONSUMED, actor, now, null);
    }

    public void returnInstallmentConsumption(StatusChangeActor actor, Instant now) {
        moveTo(StudentDiscountEvent.CONSUMPTION_RETURNED, actor, now, null);
    }

    public boolean overlapsRangeOfSameKind(DiscountType type, InvoiceType invoiceType, YearMonth from, YearMonth to) {
        return status == StudentDiscountStatus.ACTIVE && discountType == type && applicableInvoiceType == invoiceType
                && !fromPeriod.isAfter(to) && !toPeriod.isBefore(from);
    }

    public boolean coversLectiveYearOfSameKind(DiscountType type, InvoiceType invoiceType, LectiveYearSpan lectiveYear) {
        return status == StudentDiscountStatus.ACTIVE && discountType == type && applicableInvoiceType == invoiceType && lectiveYear.contains(fromPeriod);
    }

    public ScholarshipPeriodRange periodRange() {
        return new ScholarshipPeriodRange(fromPeriod, toPeriod);
    }

    public boolean coversPeriod(YearMonth period) {
        return rangeCoversPeriod(period) && status != StudentDiscountStatus.ENDED;
    }

    public boolean rangeCoversPeriod(YearMonth period) {
        boolean notRevokedBefore = revokedFromPeriod == null || period.isBefore(revokedFromPeriod);
        return periodRange().contains(period) && notRevokedBefore;
    }

    private void moveTo(StudentDiscountEvent event, StatusChangeActor actor, Instant now, String reason) {
        StudentDiscountStatus target = StudentDiscountStateMachine.requireTargetFor(status, event);
        statusChanges.add(new StudentDiscountStatusChange(this, status, target, event, actor, now, reason));
        this.status = target;
    }
}

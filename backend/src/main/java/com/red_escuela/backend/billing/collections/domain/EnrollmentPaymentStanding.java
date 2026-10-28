package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "enrollment_payment_standing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentPaymentStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EnrollmentPaymentStatus status;

    @Column(name = "suspended_since")
    private LocalDate suspendedSinceDate;

    @Column(name = "last_closed_month", nullable = false)
    private LocalDate lastClosedMonthDate;

    @OneToMany(mappedBy = "standing", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<EnrollmentPaymentStatusChange> statusChanges = new ArrayList<>();

    private EnrollmentPaymentStanding(EnrollmentKind kind, Long enrollmentId, Long studentId) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        if (kind == EnrollmentKind.PROGRAM) {
            this.programEnrollmentId = enrollmentId;
        } else {
            this.courseEnrollmentId = enrollmentId;
        }
    }

    public static EnrollmentPaymentStanding open(EnrollmentKind kind, Long enrollmentId, Long studentId, LocalDate businessDate, StatusChangeActor actor) {
        EnrollmentPaymentStanding standing = new EnrollmentPaymentStanding(kind, enrollmentId, studentId);
        standing.lastClosedMonthDate = YearMonth.from(businessDate).minusMonths(1).atDay(1);
        standing.applyEvent(EnrollmentPaymentEvent.STANDING_OPENED, businessDate, actor);
        return standing;
    }

    public void applyEvent(EnrollmentPaymentEvent event, LocalDate businessDate, StatusChangeActor actor) {
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(actor, "actor");
        EnrollmentPaymentStatus nextStatus = EnrollmentPaymentStatusStateMachine.resolveNextStatus(status, event);
        EnrollmentPaymentStatus previousStatus = status;
        this.status = nextStatus;
        this.suspendedSinceDate = nextStatus == EnrollmentPaymentStatus.SUSPENDED ? YearMonth.from(businessDate).atDay(1) : null;
        statusChanges.add(new EnrollmentPaymentStatusChange(this, previousStatus, nextStatus, event.name(), event.reason(), actor, businessDate));
    }

    public YearMonth lastClosedMonth() {
        return YearMonth.from(lastClosedMonthDate);
    }

    public boolean isMonthCloseDue(YearMonth closedMonth) {
        return lastClosedMonth().isBefore(closedMonth);
    }

    public void markMonthCloseEvaluated(YearMonth closedMonth) {
        if (isMonthCloseDue(closedMonth)) {
            this.lastClosedMonthDate = closedMonth.atDay(1);
        }
    }

    public EnrollmentKind kind() {
        return programEnrollmentId != null ? EnrollmentKind.PROGRAM : EnrollmentKind.COURSE;
    }

    public Long enrollmentId() {
        return programEnrollmentId != null ? programEnrollmentId : courseEnrollmentId;
    }

    public YearMonth suspendedSince() {
        return suspendedSinceDate == null ? null : YearMonth.from(suspendedSinceDate);
    }

    public List<EnrollmentPaymentStatusChange> statusChanges() {
        return Collections.unmodifiableList(statusChanges);
    }
}

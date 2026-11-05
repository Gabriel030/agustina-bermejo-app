package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollment_payment_status_change")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentPaymentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standing_id", nullable = false)
    private EnrollmentPaymentStanding standing;

    @Column(name = "event", nullable = false, length = 60)
    private String event;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "actor_process", length = 80)
    private String actorProcess;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    EnrollmentPaymentStatusChange(EnrollmentPaymentStanding standing, EnrollmentPaymentStatus fromStatus, EnrollmentPaymentStatus toStatus, String event,
                                  String reason, StatusChangeActor actor, LocalDate businessDate) {
        this.standing = standing;
        this.fromStatus = fromStatus == null ? null : fromStatus.name();
        this.toStatus = toStatus.name();
        this.event = event;
        this.reason = reason;
        this.actorPersonId = actor.personId();
        this.actorProcess = actor.processName();
        this.businessDate = businessDate;
        this.occurredAt = ApplicationTime.now();
    }
}

package com.red_escuela.backend.billing.payment.domain;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "refund_status_change")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @Column(name = "event", nullable = false, length = 60)
    private String event;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "actor_process", length = 80)
    private String actorProcess;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    RefundStatusChange(Refund refund, RefundStatus fromStatus, RefundStatus toStatus, RefundEvent event, String reason, StatusChangeActor actor) {
        this.refund = refund;
        this.fromStatus = fromStatus == null ? null : fromStatus.name();
        this.toStatus = toStatus.name();
        this.event = event.name();
        this.reason = reason;
        this.actorPersonId = actor.personId();
        this.actorProcess = actor.processName();
        this.occurredAt = ApplicationTime.now();
    }
}

package com.red_escuela.backend.notification.broadcast.draft.entity;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastEvent;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatusStateMachine;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastTotals;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "broadcast")
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private BroadcastCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BroadcastStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segment_spec", nullable = false, columnDefinition = "jsonb")
    private String segmentSpec;

    @Column(name = "subject_snapshot", length = 300)
    private String subjectSnapshot;

    @Column(name = "body_snapshot", nullable = false, columnDefinition = "text")
    private String bodySnapshot;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "created_by_person_id", nullable = false, updatable = false)
    private Long createdByPersonId;

    @Column(name = "confirmed_by_person_id")
    private Long confirmedByPersonId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_by_person_id")
    private Long cancelledByPersonId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "sent_total")
    private Integer sentTotal;

    @Column(name = "failed_total")
    private Integer failedTotal;

    @Column(name = "excluded_total")
    private Integer excludedTotal;

    @Column(name = "discarded_total")
    private Integer discardedTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_by_reason", columnDefinition = "jsonb")
    private String excludedByReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void confirm(Long personId, Instant now) {
        status = BroadcastStatusStateMachine.requireTargetStateFor(status, BroadcastEvent.CONFIRM);
        confirmedByPersonId = personId;
        confirmedAt = now;
    }

    public void startSending() {
        status = BroadcastStatusStateMachine.requireTargetStateFor(status, BroadcastEvent.START_SENDING);
    }

    public boolean hasFrozenTotals() {
        return sentTotal != null;
    }

    public void freezeTotals(BroadcastTotals totals) {
        sentTotal = totals.sent();
        failedTotal = totals.failed();
        excludedTotal = totals.excluded();
        discardedTotal = totals.discarded();
        excludedByReason = totals.excludedByReasonAsJson();
    }

    public void complete(Instant now) {
        status = BroadcastStatusStateMachine.requireTargetStateFor(status, BroadcastEvent.COMPLETE);
        completedAt = now;
    }

    public void cancel(Long personId, String reason, Instant now) {
        status = BroadcastStatusStateMachine.requireTargetStateFor(status, BroadcastEvent.CANCEL);
        cancelledByPersonId = personId;
        cancellationReason = reason;
        cancelledAt = now;
    }
}

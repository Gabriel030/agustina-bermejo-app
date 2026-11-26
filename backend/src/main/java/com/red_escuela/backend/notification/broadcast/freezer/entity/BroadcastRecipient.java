package com.red_escuela.backend.notification.broadcast.freezer.entity;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
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

import java.time.Instant;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "broadcast_recipient")
public class BroadcastRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broadcast_id", nullable = false, updatable = false)
    private Long broadcastId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BroadcastRecipientStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "exclusion_reason", length = 40)
    private ExclusionReason exclusionReason;

    @Column(name = "address_masked", length = 120)
    private String addressMasked;

    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void markQueued(Long queuedDeliveryId, Instant now) {
        status = BroadcastRecipientStatus.QUEUED;
        deliveryId = queuedDeliveryId;
        updatedAt = now;
    }

    public boolean isAwaitingDelivery() {
        return status == BroadcastRecipientStatus.QUEUED;
    }

    public void markSent(Instant now) {
        status = BroadcastRecipientStatus.SENT;
        updatedAt = now;
    }

    public void markFailed(Instant now) {
        status = BroadcastRecipientStatus.FAILED;
        updatedAt = now;
    }

    public void markSkipped(Instant now) {
        status = BroadcastRecipientStatus.SKIPPED;
        updatedAt = now;
    }

    public void markSkipped(Instant now, ExclusionReason reason) {
        status = BroadcastRecipientStatus.SKIPPED;
        exclusionReason = reason;
        updatedAt = now;
    }
}

package com.red_escuela.backend.notification.invitation.entity;

import com.red_escuela.backend.notification.invitation.domain.InvitationErrorCode;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientEvent;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatusStateMachine;
import com.red_escuela.backend.notification.invitation.exception.InvitationRecipientDeliveryRequiredException;
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
@Table(name = "invitation_wave_recipient")
public class InvitationWaveRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wave_id", nullable = false, updatable = false)
    private Long waveId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationRecipientStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "exclusion_reason", length = 20)
    private InvitationExclusionReason exclusionReason;

    @Column(name = "delivery_id")
    private Long deliveryId;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_code", length = 40)
    private InvitationErrorCode lastErrorCode;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static InvitationWaveRecipient pending(Long waveId, Long personId) {
        return InvitationWaveRecipient.builder().waveId(waveId).personId(personId)
                .status(InvitationRecipientStatus.PENDING).build();
    }

    public static InvitationWaveRecipient excluded(Long waveId, Long personId, InvitationExclusionReason reason) {
        return InvitationWaveRecipient.builder().waveId(waveId).personId(personId)
                .status(InvitationRecipientStatus.EXCLUDED).exclusionReason(reason).build();
    }

    public void queue(Long deliveryId) {
        if (deliveryId == null) {
            throw new InvitationRecipientDeliveryRequiredException();
        }
        status = InvitationRecipientStatusStateMachine.requireTargetStateFor(status, InvitationRecipientEvent.QUEUE);
        this.deliveryId = deliveryId;
        attempts = attempts + 1;
        lastErrorCode = null;
    }

    public void markSent(Instant now) {
        status = InvitationRecipientStatusStateMachine.requireTargetStateFor(status, InvitationRecipientEvent.MARK_SENT);
        sentAt = now;
    }

    public void markFailed(InvitationErrorCode errorCode) {
        status = InvitationRecipientStatusStateMachine.requireTargetStateFor(status, InvitationRecipientEvent.MARK_FAILED);
        lastErrorCode = errorCode;
    }

    public void exclude(InvitationExclusionReason reason) {
        status = InvitationRecipientStatusStateMachine.requireTargetStateFor(status, InvitationRecipientEvent.EXCLUDE);
        exclusionReason = reason;
    }

    public void markActivated() {
        status = InvitationRecipientStatusStateMachine.requireTargetStateFor(status, InvitationRecipientEvent.MARK_ACTIVATED);
    }
}

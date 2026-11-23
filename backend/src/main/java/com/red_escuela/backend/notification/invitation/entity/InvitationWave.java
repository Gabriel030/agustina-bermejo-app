package com.red_escuela.backend.notification.invitation.entity;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveEvent;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatusStateMachine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "invitation_wave")
public class InvitationWave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationWaveStatus status;

    @Column(name = "batch_size", nullable = false)
    private int batchSize;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(name = "daily_cap", nullable = false)
    private int dailyCap;

    @Column(name = "import_batch_id", nullable = false, updatable = false)
    private Long importBatchId;

    @Column(name = "created_by_person_id", nullable = false, updatable = false)
    private Long createdByPersonId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "started_by_person_id")
    private Long startedByPersonId;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_by_person_id")
    private Long pausedByPersonId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_person_id")
    private Long cancelledByPersonId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void start(Instant now, Long actorPersonId) {
        status = InvitationWaveStatusStateMachine.requireTargetStateFor(status, InvitationWaveEvent.START);
        startedAt = now;
        startedByPersonId = actorPersonId;
    }

    public void pause(Instant now, Long actorPersonId) {
        status = InvitationWaveStatusStateMachine.requireTargetStateFor(status, InvitationWaveEvent.PAUSE);
        pausedAt = now;
        pausedByPersonId = actorPersonId;
    }

    public void resume() {
        status = InvitationWaveStatusStateMachine.requireTargetStateFor(status, InvitationWaveEvent.RESUME);
    }

    public void complete(Instant now) {
        status = InvitationWaveStatusStateMachine.requireTargetStateFor(status, InvitationWaveEvent.COMPLETE);
        completedAt = now;
    }

    public void cancel(Instant now, Long actorPersonId) {
        status = InvitationWaveStatusStateMachine.requireTargetStateFor(status, InvitationWaveEvent.CANCEL);
        cancelledAt = now;
        cancelledByPersonId = actorPersonId;
    }
}

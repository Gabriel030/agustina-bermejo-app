package com.red_escuela.backend.enrollment.entity;

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
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "informative_session")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class InformativeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false)
    private InformativeSessionModality modality;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(name = "location")
    private String location;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "related_program_id")
    private Long relatedProgramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private InformativeSessionStatus status = InformativeSessionStatus.SCHEDULED;

    @Column(name = "created_by_id", nullable = false)
    private Long createdById;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isScheduled() {
        return status == InformativeSessionStatus.SCHEDULED;
    }

    public boolean hasStartedBy(LocalDateTime now) {
        return !scheduledAt.isAfter(now);
    }

    public boolean hasRoomFor(long registrations) {
        return capacity == null || registrations < capacity;
    }

    public void markAsCancelled() {
        status = InformativeSessionStatus.CANCELLED;
    }
}

package com.red_escuela.backend.attendance.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.shared.time.ApplicationTime;

@Getter
@Setter
@Entity
@Table(name = "attendance_change_request")
public class AttendanceChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Instructor requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status", nullable = false)
    private AttendanceStatus requestedStatus;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceChangeRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private Person approvedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverted_by_id")
    private Person revertedBy;

    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    @Column(name = "revert_reason", length = 500)
    private String revertReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void reopenReverted(Person actor, String reason, LocalDateTime at) {
        reopen();
        this.revertedBy = actor;
        this.revertedAt = at;
        this.revertReason = reason;
    }

    public void reopen() {
        this.status = AttendanceChangeRequestStatus.PENDING;
        this.approvedBy = null;
        this.approvedAt = null;
        this.rejectionReason = null;
    }

    @PrePersist
    private void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = ApplicationTime.now();
        }
        if (this.status == null) {
            this.status = AttendanceChangeRequestStatus.PENDING;
        }
    }
}

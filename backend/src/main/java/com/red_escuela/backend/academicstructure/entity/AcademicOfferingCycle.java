package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.exception.CycleNotEditableException;
import com.red_escuela.backend.academicstructure.exception.CycleNotPendingApprovalException;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "academic_offering_cycle")
public class AcademicOfferingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AcademicOfferingCycleStatus status = AcademicOfferingCycleStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Person decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    public boolean isEditable() {
        return status == AcademicOfferingCycleStatus.DRAFT || status == AcademicOfferingCycleStatus.REJECTED;
    }

    public boolean wasApproved() {
        return status == AcademicOfferingCycleStatus.APPROVED || status == AcademicOfferingCycleStatus.CLOSED;
    }

    public void ensureEditable() {
        if (!isEditable()) {
            throw new CycleNotEditableException(id, status);
        }
    }

    public void submitForApproval() {
        ensureEditable();
        status = AcademicOfferingCycleStatus.PENDING_APPROVAL;
        decidedBy = null;
        decidedAt = null;
        rejectionReason = null;
    }

    public void approve(Person director, LocalDateTime decidedAt) {
        ensurePendingApproval();
        status = AcademicOfferingCycleStatus.APPROVED;
        this.decidedBy = director;
        this.decidedAt = decidedAt;
    }

    public void reject(Person director, String reason, LocalDateTime decidedAt) {
        ensurePendingApproval();
        status = AcademicOfferingCycleStatus.REJECTED;
        this.decidedBy = director;
        this.decidedAt = decidedAt;
        this.rejectionReason = reason;
    }

    private void ensurePendingApproval() {
        if (status != AcademicOfferingCycleStatus.PENDING_APPROVAL) {
            throw new CycleNotPendingApprovalException(id);
        }
    }
}

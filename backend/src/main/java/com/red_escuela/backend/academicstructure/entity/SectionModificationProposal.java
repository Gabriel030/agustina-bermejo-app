package com.red_escuela.backend.academicstructure.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "section_modification_proposal")
public class SectionModificationProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_section_id", nullable = false)
    private SubjectSection subjectSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SectionModificationStatus status;

    @Column(name = "replaces_schedule", nullable = false)
    private boolean replacesSchedule;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "section_modification_proposal_slot", joinColumns = @JoinColumn(name = "proposal_id"))
    private List<ProposedSlot> slots = new ArrayList<>();

    public static SectionModificationProposal pending(
            SubjectSection section, boolean replacesSchedule, List<ProposedSlot> slots, LocalDateTime requestedAt) {
        var proposal = new SectionModificationProposal();
        proposal.subjectSection = section;
        proposal.status = SectionModificationStatus.PENDING;
        proposal.replacesSchedule = replacesSchedule;
        proposal.requestedAt = requestedAt;
        proposal.slots = new ArrayList<>(slots);
        return proposal;
    }

    public void approve(LocalDateTime resolvedAt) {
        ensurePending();
        this.status = SectionModificationStatus.APPROVED;
        this.resolvedAt = resolvedAt;
    }

    public void reject(String reason, LocalDateTime resolvedAt) {
        ensurePending();
        this.status = SectionModificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.resolvedAt = resolvedAt;
    }

    public boolean isPending() {
        return status == SectionModificationStatus.PENDING;
    }

    private void ensurePending() {
        if (!isPending()) {
            throw new IllegalStateException("Section modification proposal is not pending: " + id);
        }
    }
}

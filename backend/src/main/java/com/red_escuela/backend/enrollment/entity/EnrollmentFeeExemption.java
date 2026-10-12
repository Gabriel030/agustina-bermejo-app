package com.red_escuela.backend.enrollment.entity;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionAlreadyRevokedException;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "enrollment_fee_exemption")
public class EnrollmentFeeExemption {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_enrollment_id", nullable = false)
    private ProgramEnrollment programEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ProgramYearBillingTemplate template;

    @Column(name = "slot", nullable = false)
    private Integer slot;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by_id", nullable = false)
    private Person grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private Person revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_notes", length = 500)
    private String revocationNotes;

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(Person actor, LocalDateTime at, String notes) {
        if (!isActive()) {
            throw new EnrollmentFeeExemptionAlreadyRevokedException(id);
        }
        this.revokedBy = actor;
        this.revokedAt = at;
        this.revocationNotes = notes;
    }
}

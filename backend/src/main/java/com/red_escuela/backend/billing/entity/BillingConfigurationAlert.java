package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.academicstructure.entity.Program;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Getter
@Setter
@Entity
@Table(name = "billing_configuration_alert")
public class BillingConfigurationAlert {

    public static final String REASON_YEAR_NOT_CONFIGURED = "YEAR_NOT_CONFIGURED";
    public static final String REASON_YEAR_GAP = "YEAR_GAP";
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "period", nullable = false)
    private YearMonth period;

    @Column(name = "reason", nullable = false, length = 30)
    private String reason = REASON_YEAR_NOT_CONFIGURED;

    @Column(name = "affected_enrollments", nullable = false)
    private Integer affectedEnrollments;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private LocalDateTime lastDetectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public boolean isOpen() {
        return resolvedAt == null;
    }
}

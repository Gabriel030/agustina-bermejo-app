package com.red_escuela.backend.billing.entity;

import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.persistence.PercentageConverter;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import com.red_escuela.backend.identity.entity.Person;

@Getter
@Setter
@Entity
@Table(name = "program_cohort", uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "intake"}))
public class ProgramCohort {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ProgramYearBillingTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "intake", nullable = false)
    private CohortIntake intake;

    @Column(name = "admission_start", nullable = false)
    private LocalDate admissionStart;

    @Column(name = "admission_end", nullable = false)
    private LocalDate admissionEnd;

    @Column(name = "cursada_start", nullable = false)
    private LocalDate cursadaStart;

    @Column(name = "enrollment_fee_month", nullable = false)
    private Integer enrollmentFeeMonth;

    @Convert(converter = PercentageConverter.class)
    @Column(name = "entry_fee_discount", nullable = false)
    private Percentage entryFeeDiscount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isAdmissionOpenOn(LocalDate day) {
        return !day.isBefore(admissionStart) && !day.isAfter(admissionEnd);
    }

    public boolean isEnrollmentFeeMonth(YearMonth period) {
        return enrollmentFeeMonth != null && period.getMonthValue() == enrollmentFeeMonth;
    }

    @PrePersist
    void onCreate() {
        if (enrollmentFeeMonth == null) {
            enrollmentFeeMonth = 2;
        }
        if (entryFeeDiscount == null) {
            entryFeeDiscount = Percentage.ofFraction(intake == CohortIntake.SECOND ? new BigDecimal("0.5") : BigDecimal.ZERO);
        }
        if (this.createdAt == null) {
            this.createdAt = ApplicationTime.now();
        }
    }
}

package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import com.red_escuela.backend.shared.domain.Money;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;

@Getter
@Setter
@Entity
@Table(name = "price_revision")
public class PriceRevision {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "monthly_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "monthly_amount_currency", nullable = false))
    })
    private Money monthlyAmount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

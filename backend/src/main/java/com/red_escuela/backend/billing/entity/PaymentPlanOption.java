package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;

@Getter
@Setter
@Entity
@Table(name = "payment_plan_option")
public class PaymentPlanOption {

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

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", nullable = false)
    private PaymentPlanCadence cadence;

    // Only meaningful if cadence = UPFRONT. ColumnTransformer, no JdbcTypeCode — ver
    // InvoiceAdjustment.value para el porqué.
    @Convert(converter = MonetaryAdjustmentValueConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "upfront_discount", columnDefinition = "jsonb")
    private MonetaryAdjustmentValue upfrontDiscount;

    // The Director can turn this window on/off per program and per year — not a fixed benefit.
    @Column(name = "upfront_window_enabled", nullable = false)
    private boolean upfrontWindowEnabled;

    @Column(name = "upfront_window_start")
    private LocalDate upfrontWindowStart;

    @Column(name = "upfront_window_end")
    private LocalDate upfrontWindowEnd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

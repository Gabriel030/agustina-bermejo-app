package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "program")
public class Program {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    // Monthly recurring fee (RECURRING_FEE) amount — mirrored into a PriceRevision on create/update
    // (Fase 6.1, PriceRevisionService).
    @Column(name = "price")
    private BigDecimal price;

    // Enrollment fee (ENROLLMENT_FEE, matrícula) — separate from the monthly tuition above,
    // also mirrored into its own PriceRevision (Fase 6.1).
    @Column(name = "enrollment_fee_amount")
    private BigDecimal enrollmentFeeAmount;

    @Column(name = "total_years")
    private Integer totalYears;

    @Column(name = "total_credits_required", nullable = false)
    private Integer totalCreditsRequired = 0;

    @OneToMany(mappedBy = "program", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubject> programSubjectsList;
}

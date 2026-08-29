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

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "total_years")
    private Integer totalYears;

    @Column(name = "total_credits_required", nullable = false)
    private Integer totalCreditsRequired = 0;

    // Épica 8 Fase 2: junto a totalYears arma el plan de pagos completo
    // (total de cuotas = totalYears × cuotasPorAnioLectivo). La ventana de admisión
    // pasó a vivir en ProgramCohort — una carrera tiene exactamente 2 por año.
    @Column(name = "cuotas_por_anio_lectivo")
    private Integer cuotasPorAnioLectivo;

    @OneToMany(mappedBy = "program", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubject> programSubjectsList;
}

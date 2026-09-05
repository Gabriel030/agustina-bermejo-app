package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Descuento segmentado — aplica a todos los alumnos de una carrera o un curso
 * (uno de los dos, nunca los dos), dentro de un rango de vigencia. Se suma con
 * cualquier {@link Discount} individual que también le aplique al mismo alumno.
 * Solo PORCENTAJE o MONTO_FIJO (a diferencia de Discount, una promoción segmentada
 * no tiene sentido como beca total — validado en el service, no en el enum
 * compartido {@link DiscountType} para no duplicar el tipo).
 */
@Getter
@Setter
@Entity
@Table(name = "promotion")
public class Promotion {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private DiscountType type;

    @Column(name = "valor", nullable = false)
    private BigDecimal amount;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate validFrom;

    @Column(name = "vigente_hasta")
    private LocalDate validUntil;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean validOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    public boolean appliesToProgram(Long programId) {
        return program != null && program.getId().equals(programId);
    }

    public boolean appliesToCourse(Long courseId) {
        return course != null && course.getId().equals(courseId);
    }
}

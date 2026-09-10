package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import com.red_escuela.backend.entity.StatusType;

@Getter
@Setter
@Entity
@Table(name = "term")
public class Term {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_number")
    private Integer year;

    @Column(name = "description")
    private String description;

    @Column(name = "type")
    private String type;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "enrollment_start")
    private LocalDateTime enrollmentStart;

    @Column(name = "enrollment_end")
    private LocalDateTime enrollmentEnd;

    // null = sin override, se sigue validando por enrollmentStart/enrollmentEnd (default).
    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_override")
    private TermEnrollmentOverride enrollmentOverride;

    // Invariante propia del Term (decisión 2026-09-03): "¿este mes cae dentro de mi período
    // lectivo?" — antes vivía duplicada en cada consumidor (facturación de carrera, facturación
    // de curso). Sin startDate/endDate configurados no hay ventana real que hacer cumplir
    // (fixtures viejas/incompletas): no bloquea, no es el caso esperado en producción.
    public boolean contains(YearMonth period) {
        if (startDate == null || endDate == null) {
            return true;
        }
        YearMonth start = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        return !period.isBefore(start) && !period.isAfter(end);
    }

    // Cantidad de meses calendario que dura este Term, inclusive en ambas puntas (marzo a junio
    // = 4 meses). null = sin fechas configuradas, no hay span que calcular.
    public Long spanInMonths() {
        if (startDate == null || endDate == null) {
            return null;
        }
        return ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(endDate)) + 1;
    }
}

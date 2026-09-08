package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.red_escuela.backend.entity.StatusType;

@Getter
@Setter
@Entity
@Table(name = "course")
public class Course {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubject> courseSubjectList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(name = "schedule")
    private String schedule;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType isActive;

    @Column(name = "registration_start")
    private LocalDateTime registrationStart;

    @Column(name = "registration_end")
    private LocalDateTime registrationEnd;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "duration_months")
    private Integer durationMonths;

    // null = sin corte automático configurado, la lista de espera queda abierta indefinidamente.
    // Positivo = días DESPUÉS de term.startDate; negativo = días ANTES. La fecha de corte
    // resultante la calcula CourseEnrollmentWaitlistExpiryJob, no vive acá como fecha absoluta
    // porque depende de term.startDate, que puede cambiar independientemente de este curso.
    @Column(name = "waitlist_deadline_offset_days")
    private Integer waitlistDeadlineOffsetDays;

    // Invariante propia (decisión 2026-09-03): "¿tengo lugar para uno más?" — antes se calculaba
    // afuera, en CourseEnrollmentService, reimplementando la misma regla que ya usan
    // CourseSubjectSection/SubjectSection. El caller es responsable de contar cuántos lugares
    // están ocupados (requiere una consulta con lock, ajena al dominio de este objeto).
    public boolean hasCapacityFor(long occupiedCount) {
        return capacity == null || occupiedCount < capacity;
    }

    // Un Course no progresa de año como un ProgramEnrollment — un único Term y un único
    // durationMonths para toda su vida, así que la ventana de facturación es más simple que la
    // de ProgramYearBillingTemplate (ver su canGenerateInvoice), pero la misma idea: cae dentro
    // de mi Term y no superó mi tope de cuotas.
    public boolean canGenerateInvoice(YearMonth period, long invoicesGeneratedInWindow) {
        return term.contains(period) && (durationMonths == null || invoicesGeneratedInWindow < durationMonths);
    }

    public Integer remainingInvoices(long invoicesGeneratedInWindow) {
        return durationMonths == null ? null : durationMonths - (int) invoicesGeneratedInWindow;
    }

    // Fecha de corte real de la lista de espera — null si no hay corte configurado. Vive acá
    // (no como columna calculada) porque depende de term.startDate, que puede cambiar
    // independientemente de este curso.
    public LocalDate waitlistDeadline() {
        return waitlistDeadlineOffsetDays == null ? null : term.getStartDate().toLocalDate().plusDays(waitlistDeadlineOffsetDays);
    }

    public boolean waitlistDeadlineHasPassed(LocalDate today) {
        LocalDate deadline = waitlistDeadline();
        return deadline != null && !today.isBefore(deadline);
    }
}


package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.academicstructure.entity.Program;

@Getter
@Setter
@Entity
@Table(name = "program_enrollment")
public class ProgramEnrollment implements BillableEnrollment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    // Cohorte de ORIGEN — fija desde la matriculación, nunca cambia (mismo criterio que usa la
    // industria para "cohort year/term": identifica cuándo entró el alumno, no en qué año de
    // cursada está ahora — ver ProgramEnrollmentBillingScheduler, la matrícula/50% de la cohorte
    // SECOND se resuelve una sola vez contra ESTA cohorte, nunca se recalcula).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_cohort_id")
    private ProgramCohort programCohort;

    // Plantilla del año lectivo CORRIENTE para efectos de facturación — a diferencia de
    // programCohort, esto SÍ avanza con el tiempo (ProgramEnrollmentService.avanzarAñoLectivo):
    // un alumno puede repetir un año (rendir de nuevo las mismas materias), así que la cantidad
    // de años facturados no tiene por qué coincidir con Program.totalYears. Nace en la plantilla
    // de programCohort, nullable solo por retrocompatibilidad con filas viejas sin cohorte.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_billing_template_id")
    private ProgramYearBillingTemplate currentBillingTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProgramEnrollmentStatus status;

    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    // Mantenido por EnrollmentBillingComplianceSweepService (Fase 6.3) — ver BillableEnrollment.
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_standing", nullable = false)
    private EnrollmentBillingStanding billingStanding = EnrollmentBillingStanding.UP_TO_DATE;

    @Column(name = "billing_standing_updated_at")
    private LocalDateTime billingStandingUpdatedAt;

    @OneToMany(mappedBy = "programEnrollment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = false)
    private List<SubjectSectionEnrollment> sectionEnrollments;

    // Invariante propia (decisión 2026-09-03): la matrícula (ENROLLMENT_FEE) paga habilita
    // inscribirse a materias — nace una sola vez por ProgramEnrollment, nunca por año lectivo
    // (ver ProgramEnrollmentBillingScheduler.generarMatricula: solo corre en la primera
    // selección de plan de pago, programCohort nunca cambia). El caller es responsable de
    // buscar esa Invoice (esta entidad no conoce Invoice ni InvoiceRepository).
    //
    // Sin programCohort, esta regla no aplica: ver el comentario de matricular() en
    // ProgramEnrollmentService — cohortId es nullable a propósito (alta directa por staff sin
    // atarla a facturación, y ~10 fixtures de Épica 2 anteriores a Fase 6.1 que no tienen nada
    // que ver con cobros), y generarMatricula ni siquiera puede generar la Invoice sin cohorte.
    // Exigir matrícula pagada ahí bloquearía para siempre una matriculación que por diseño nunca
    // va a tener una Invoice de matrícula.
    public boolean canEnrollInSubjects(Invoice enrollmentFeeInvoice) {
        if (programCohort == null) {
            return true;
        }
        return enrollmentFeeInvoice != null
                && (enrollmentFeeInvoice.getStatus() == InvoiceStatus.PAID || enrollmentFeeInvoice.getStatus() == InvoiceStatus.WAIVED);
    }
}

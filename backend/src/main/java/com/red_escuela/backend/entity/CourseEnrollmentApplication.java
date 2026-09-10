package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.academicstructure.entity.Course;

/**
 * Vínculo intent→resultado entre el pago de la primera cuota de un curso y la
 * {@link CourseEnrollment} real que se crea recién cuando ese pago se aprueba
 * (ver PaymentReceiptService.aprobar). A diferencia de {@link
 * ProgramEnrollmentApplication}, esta entidad NO modela su propia transición de
 * estado — no hace falta: no hay "solicitud" que alguien apruebe/rechace sobre
 * el curso en sí (decisión de negocio ya tomada en Épica 7 Fase 1), el único
 * estado real es el de {@link #paymentReceipt}.getStatus(). Esta entidad solo
 * recuerda para qué curso y con qué overrides de comisión, hasta que
 * courseEnrollment deja de ser null.
 */
@Getter
@Setter
@Entity
@Table(name = "course_enrollment_application")
public class CourseEnrollmentApplication {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // JSON serializado de Map<courseSubjectId, sectionId> — mismo contrato que
    // CreateCourseEnrollmentRequest.sectionOverrides, persistido para poder
    // matricular recién cuando se aprueba el comprobante (ver PaymentReceiptService).
    @Column(name = "section_overrides")
    private String sectionOverrides;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_receipt_id", nullable = false)
    private PaymentReceipt paymentReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_enrollment_id")
    private CourseEnrollment courseEnrollment;
}

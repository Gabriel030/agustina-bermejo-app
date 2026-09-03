package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "invoice")
public class Invoice {
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

    // Épica 8 Fase 2: exactamente uno de los dos (o ninguno, para facturación
    // ad-hoc vía InvoiceService.crear) — vínculo real a la matriculación que
    // generó esta factura, junto con installmentNumber/totalInstallments. Sin
    // esto no había forma de saber programáticamente "esta es la cuota 4 de 10
    // de tal matriculación", solo un concept de texto libre.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_enrollment_id")
    private ProgramEnrollment programEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_enrollment_id")
    private CourseEnrollment courseEnrollment;

    // Null para matrícula (no es "cuota N de M") y para facturación ad-hoc.
    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "total_installments")
    private Integer totalInstallments;

    @Column(name = "concept", nullable = false)
    private String concept;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments;
}

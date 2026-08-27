package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Registro de auditoría de emisión (ajuste de producto sobre la spec original: sí se quiere
// historial). El PDF en sí sigue sin persistirse — se genera on-demand — solo se guarda quién
// pidió qué constancia y cuándo.
@Getter
@Setter
@Entity
@Table(name = "certificate_issuance")
public class CertificateIssuance {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CertificateType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_enrollment_id", nullable = false)
    private ProgramEnrollment programEnrollment;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Person requestedBy;
}

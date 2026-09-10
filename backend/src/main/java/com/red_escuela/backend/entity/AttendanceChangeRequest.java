package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.Instructor;

/*
 * Solicitud del Docente para cambiar el estado de una asistencia ya cargada (ej. Ausente ->
 * Presente). A diferencia de la justificación (Attendance.justify, que solo agrega contexto a
 * un Ausente), esto permite cambiar a CUALQUIER estado, pero nunca impacta directo: siempre
 * queda PENDING hasta que Preceptor/Administrativo la resuelve (mismos roles que ya aprueban
 * justificaciones, ver AttendanceController.aprobarJustificacion).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_change_request")
public class AttendanceChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Instructor requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status", nullable = false)
    private AttendanceStatus requestedStatus;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceChangeRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private Person approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = AttendanceChangeRequestStatus.PENDING;
        }
    }
}

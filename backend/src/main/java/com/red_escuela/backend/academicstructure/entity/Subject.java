package com.red_escuela.backend.academicstructure.entity;

import org.springframework.modulith.NamedInterface;

import com.red_escuela.backend.academicstructure.exception.IncompleteSubjectException;
import com.red_escuela.backend.academicstructure.exception.SubjectInvalidTransitionException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotAssignableToPlanException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NamedInterface("model")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subject")
public class Subject {
    public static final int DEFAULT_MAX_FINAL_EXAM_ATTEMPTS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 4)
    private String code;

    @Column(name = "description")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubjectStatus status = SubjectStatus.DRAFT;

    @Builder.Default
    @Column(name = "max_final_exam_attempts", nullable = false)
    private Integer maxFinalExamAttempts = DEFAULT_MAX_FINAL_EXAM_ATTEMPTS;

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(name = "weekly_hours_lab")
    private Integer weeklyHoursLab;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_mode")
    private GradeMode gradeMode;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public void requireEditable() {
        if (status == SubjectStatus.PENDING_APPROVAL) {
            throw new SubjectInvalidTransitionException(id, status, "editar");
        }
    }

    public void changeCode(String newCode) {
        if (newCode.equals(code)) {
            return;
        }
        if (status != SubjectStatus.DRAFT && status != SubjectStatus.REJECTED) {
            throw new SubjectInvalidTransitionException(id, status, "cambiar el código de");
        }
        code = newCode;
    }

    public void submitForApproval() {
        if (status != SubjectStatus.DRAFT && status != SubjectStatus.REJECTED) {
            throw new SubjectInvalidTransitionException(id, status, "enviar a aprobación");
        }
        if (!hasWeeklyHours()) {
            throw new IncompleteSubjectException(id, "al menos un tipo de horas semanales es obligatorio");
        }
        if (gradeMode == null) {
            throw new IncompleteSubjectException(id, "la modalidad de calificación es obligatoria");
        }
        status = SubjectStatus.PENDING_APPROVAL;
        rejectionReason = null;
    }

    public void approve() {
        requirePendingApproval("aprobar");
        status = SubjectStatus.ACTIVE;
    }

    public void reject(String reason) {
        requirePendingApproval("rechazar");
        status = SubjectStatus.REJECTED;
        rejectionReason = reason;
    }

    public void deactivate() {
        if (status != SubjectStatus.ACTIVE) {
            throw new SubjectInvalidTransitionException(id, status, "desactivar");
        }
        status = SubjectStatus.INACTIVE;
    }

    public void reactivate() {
        if (status != SubjectStatus.INACTIVE) {
            throw new SubjectInvalidTransitionException(id, status, "reactivar");
        }
        status = SubjectStatus.ACTIVE;
    }

    public void requireAssignableToPlan() {
        if (status == SubjectStatus.PENDING_APPROVAL || status == SubjectStatus.REJECTED) {
            throw new SubjectNotAssignableToPlanException(id, status);
        }
    }

    private boolean hasWeeklyHours() {
        return positive(weeklyHours) || positive(weeklyHoursLab);
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private void requirePendingApproval(String action) {
        if (status != SubjectStatus.PENDING_APPROVAL) {
            throw new SubjectInvalidTransitionException(id, status, action);
        }
    }

    public void setMaxFinalExamAttempts(Integer maxFinalExamAttempts) {
        this.maxFinalExamAttempts = maxFinalExamAttempts == null ? DEFAULT_MAX_FINAL_EXAM_ATTEMPTS : maxFinalExamAttempts;
    }
}

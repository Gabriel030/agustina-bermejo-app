package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.domain.CurriculumPlanNotOpenForNewStudentsException;
import com.red_escuela.backend.academicstructure.domain.InvalidCurriculumPlanDatesException;
import com.red_escuela.backend.academicstructure.domain.InvalidCurriculumPlanTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("model")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "curriculum_plan")
public class CurriculumPlan {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Program program;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CurriculumPlanStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "graduation_deadline")
    private LocalDate graduationDeadline;

    @Column(name = "total_years")
    private Integer totalYears;

    @Builder.Default
    @Column(name = "total_credits_required", nullable = false)
    private Integer totalCreditsRequired = 0;

    @Column(name = "total_hours_required")
    private Integer totalHoursRequired;

    public static CurriculumPlan initialVersionOf(Program program, LocalDate validFrom) {
        return CurriculumPlan.builder()
                .program(program)
                .versionNumber(1)
                .name(program.getName() + " - Plan v1")
                .status(CurriculumPlanStatus.ACTIVE)
                .validFrom(validFrom)
                .totalYears(program.getTotalYears())
                .totalCreditsRequired(program.getTotalCreditsRequired())
                .totalHoursRequired(program.getTotalHoursRequired())
                .build();
    }

    public static CurriculumPlan draftVersionOf(Program program, int versionNumber, String name, LocalDate validFrom,
                                                Integer totalYears, Integer totalCreditsRequired, Integer totalHoursRequired) {
        return CurriculumPlan.builder()
                .program(program)
                .versionNumber(versionNumber)
                .name(name)
                .status(CurriculumPlanStatus.DRAFT)
                .validFrom(validFrom)
                .totalYears(totalYears)
                .totalCreditsRequired(totalCreditsRequired == null ? 0 : totalCreditsRequired)
                .totalHoursRequired(totalHoursRequired)
                .build();
    }

    public boolean isDraft() {
        return status == CurriculumPlanStatus.DRAFT;
    }

    public void requireActivatable() {
        if (status != CurriculumPlanStatus.DRAFT) {
            throw new InvalidCurriculumPlanTransitionException(id, status.name(), CurriculumPlanStatus.ACTIVE.name());
        }
    }

    public void activate() {
        requireActivatable();
        status = CurriculumPlanStatus.ACTIVE;
    }

    public void expire(LocalDate expiresOn, LocalDate graduationDeadline) {
        if (status != CurriculumPlanStatus.ACTIVE) {
            throw new InvalidCurriculumPlanTransitionException(id, status.name(), CurriculumPlanStatus.EXPIRED.name());
        }
        requireValidityRange(validFrom, expiresOn, graduationDeadline);
        status = CurriculumPlanStatus.EXPIRED;
        validUntil = expiresOn;
        this.graduationDeadline = graduationDeadline;
    }

    public void reschedule(LocalDate newValidFrom, LocalDate newGraduationDeadline) {
        LocalDate effectiveValidFrom = newValidFrom == null ? validFrom : newValidFrom;
        LocalDate effectiveDeadline = newGraduationDeadline == null ? graduationDeadline : newGraduationDeadline;
        requireValidityRange(effectiveValidFrom, validUntil, effectiveDeadline);
        validFrom = effectiveValidFrom;
        graduationDeadline = effectiveDeadline;
    }

    public void changeTotals(Integer newTotalYears, Integer newTotalCreditsRequired, Integer newTotalHoursRequired) {
        if (newTotalYears != null) {
            totalYears = newTotalYears;
        }
        if (newTotalCreditsRequired != null) {
            totalCreditsRequired = newTotalCreditsRequired;
        }
        if (newTotalHoursRequired != null) {
            totalHoursRequired = newTotalHoursRequired;
        }
    }

    public void requireOpenForNewStudents(LocalDate today) {
        boolean notStartedYet = validFrom != null && today.isBefore(validFrom);
        boolean pastValidity = validUntil != null && today.isAfter(validUntil);
        if (status != CurriculumPlanStatus.ACTIVE || notStartedYet || pastValidity) {
            throw new CurriculumPlanNotOpenForNewStudentsException(id);
        }
    }

    public boolean allowsExistingStudentsToContinue(LocalDate today) {
        if (status == CurriculumPlanStatus.DRAFT) {
            return false;
        }
        return graduationDeadline == null || !today.isAfter(graduationDeadline);
    }

    private static void requireValidityRange(LocalDate from, LocalDate until, LocalDate deadline) {
        if (until != null && from != null && until.isBefore(from)) {
            throw new InvalidCurriculumPlanDatesException("el vencimiento es anterior al inicio de vigencia");
        }
        if (deadline != null && until != null && deadline.isBefore(until)) {
            throw new InvalidCurriculumPlanDatesException("la fecha límite de egreso es anterior al vencimiento");
        }
    }
}

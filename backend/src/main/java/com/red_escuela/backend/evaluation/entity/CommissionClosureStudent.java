package com.red_escuela.backend.evaluation.entity;

import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commission_closure_student")
public class CommissionClosureStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closure_id", nullable = false)
    private Long closureId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_condition", nullable = false)
    private ClosureFinalCondition finalCondition;

    @Column(name = "promotion_grade")
    private Grade promotionGrade;

    @Column(name = "attendance_ok")
    private Boolean attendanceOk;

    @Column(name = "grade_ok")
    private Boolean gradeOk;

    @Column(name = "administrative_ok")
    private Boolean administrativeOk;

    @Column(name = "documents_complete")
    private Boolean documentsComplete;

    @Column(name = "correlatives_ok")
    private Boolean correlativesOk;

    @Column(name = "teacher_observation", length = 500)
    private String teacherObservation;
}

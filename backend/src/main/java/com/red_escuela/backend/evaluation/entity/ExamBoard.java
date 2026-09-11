package com.red_escuela.backend.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.Term;

/*
 * Mesa de examen final, organizada por Subject/CourseSubject + Term (no por comisión):
 * un alumno regular en una materia puede rendir en cualquier mesa de esa materia en ese
 * período, sin importar en qué comisión cursó — coherente con que las materias ya se
 * comparten entre carreras. XOR subject/courseSubject con el mismo patrón de GradeItem.
 */
@Getter
@Setter
@Entity
@Table(name = "exam_board")
public class ExamBoard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_subject_id")
    private CourseSubject courseSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(name = "exam_date", nullable = false)
    private LocalDateTime examDate;

    // Texto libre por ahora (todavía no está definido quién/cómo se asigna el aula real de
    // cada mesa) — nullable, se completa cuando se sepa.
    @Column(name = "classroom")
    private String classroom;

    @Column(name = "registration_start")
    private LocalDateTime registrationStart;

    @Column(name = "registration_end")
    private LocalDateTime registrationEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExamBoardStatus status;

    @OneToMany(mappedBy = "examBoard", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamBoardInstructor> tribunal;

    @PrePersist
    @PreUpdate
    private void validateSingleTarget() {
        boolean hasSubject = this.subject != null;
        boolean hasCourseSubject = this.courseSubject != null;
        if (hasSubject == hasCourseSubject) {
            throw new IllegalStateException("ExamBoard must reference exactly one target: subject OR courseSubject.");
        }
    }
}

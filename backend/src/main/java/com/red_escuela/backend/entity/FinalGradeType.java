package com.red_escuela.backend.entity;

// Vía por la que una cursada quedó APPROVED — FINAL_EXAM (rindió y aprobó el examen final,
// EvaluationScheme.hasFinal = true) o PROMOTION (promocionó sin final, ambos parciales
// aprobados con nota >= minApprovalGrade, EvaluationScheme.hasFinal = false). Nunca un
// EnrollmentStatus nuevo separado de APPROVED: correlativas y "materias aprobadas" ya
// filtran por APPROVED en varios services, y promoción es, a todo efecto de esas
// validaciones, una aprobación real — este campo solo aclara CÓMO se llegó a ella.
public enum FinalGradeType {
    FINAL_EXAM,
    PROMOTION
}

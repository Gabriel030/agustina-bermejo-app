package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ExamBoardRegistration;

// Vista rica de "mi inscripción a una mesa" para GET /me/exam-board-registrations — a
// diferencia de ExamBoardRegistrationResponse (genérica, usada por staff en
// ExamBoardController.listarAnotados), acá el alumno necesita ver la mesa completa (fecha,
// aula) y su nota si ya rindió, no solo los ids. `score` viene de cruzar contra GradeItem (ver
// ExamBoardRegistrationService.listarMisInscripciones + MeExamBoardRegistrationController) —
// ExamBoardRegistrationStatus.GRADED nunca se setea en el código actual, así que no se puede
// confiar en el status de la inscripción para saber si ya rindió.
public record MyExamBoardRegistrationResponse(
        Long id, Long enrollmentId, ExamBoardResponse examBoard, String status, Double score) {

    public static MyExamBoardRegistrationResponse from(ExamBoardRegistration registration, Double score) {
        return new MyExamBoardRegistrationResponse(
                registration.getId(),
                registration.getProgramSubjectSectionEnrollment().getId(),
                ExamBoardResponse.from(registration.getExamBoard()),
                registration.getStatus().name(),
                score);
    }
}

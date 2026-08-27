package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateExamBoardRegistrationRequest;
import com.red_escuela.backend.dto.ExamBoardRegistrationResponse;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.ExamBoardRegistrationService;
import com.red_escuela.backend.service.SubjectSectionEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-AL-05/06: autoservicio del alumno para inscribirse/darse de baja de una mesa de examen
 * final. Igual que el resto de /me/..., alcance limitado al lado "program" por ahora.
 */
@RestController
@RequestMapping("/me")
public class MeExamBoardRegistrationController {

    private final CurrentStudentService currentStudentService;
    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    private final ExamBoardRegistrationService examBoardRegistrationService;

    public MeExamBoardRegistrationController(
            CurrentStudentService currentStudentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            ExamBoardRegistrationService examBoardRegistrationService) {
        this.currentStudentService = currentStudentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.examBoardRegistrationService = examBoardRegistrationService;
    }

    @PostMapping("/exam-board-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ExamBoardRegistrationResponse inscribirme(@Valid @RequestBody CreateExamBoardRegistrationRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentService.verDetalle(request.programSectionEnrollmentId());
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new SectionEnrollmentNotFoundException(request.programSectionEnrollmentId());
        }
        return ExamBoardRegistrationResponse.from(
                examBoardRegistrationService.inscribirProgramEnrollment(request.examBoardId(), request.programSectionEnrollmentId()));
    }

    @PostMapping("/exam-board-registrations/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void darmeDeBaja(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ExamBoardRegistration registration = examBoardRegistrationService.verDetalle(id);
        SubjectSectionEnrollment enrollment = registration.getProgramSubjectSectionEnrollment();
        if (enrollment == null || !enrollment.getStudent().getId().equals(student.getId())) {
            throw new ExamBoardRegistrationNotFoundException(id);
        }
        examBoardRegistrationService.darDeBaja(id);
    }
}

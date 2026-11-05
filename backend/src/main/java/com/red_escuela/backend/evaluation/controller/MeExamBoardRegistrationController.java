package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CreateExamBoardRegistrationRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.dto.MyExamBoardRegistrationResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.evaluation.service.ExamBoardRegistrationService;
import com.red_escuela.backend.evaluation.service.ExamBoardService;
import com.red_escuela.backend.evaluation.service.GradeItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeExamBoardRegistrationController {

    private final CurrentStudentService currentStudentService;
    private final ExamBoardRegistrationService examBoardRegistrationService;
    private final ExamBoardService examBoardService;
    private final GradeItemService gradeItemService;

    public MeExamBoardRegistrationController(
            CurrentStudentService currentStudentService,
            ExamBoardRegistrationService examBoardRegistrationService,
            ExamBoardService examBoardService,
            GradeItemService gradeItemService) {
        this.currentStudentService = currentStudentService;
        this.examBoardRegistrationService = examBoardRegistrationService;
        this.examBoardService = examBoardService;
        this.gradeItemService = gradeItemService;
    }

    @GetMapping("/exam-boards")
    public List<ExamBoardResponse> availableExamBoards(@RequestParam Long subjectId) {
        return examBoardService.listAvailableBySubject(subjectId).stream()
                .map(ExamBoardResponse::from)
                .toList();
    }

    @GetMapping("/exam-board-registrations")
    public List<MyExamBoardRegistrationResponse> mine() {
        Student student = currentStudentService.resolveCurrentStudent();
        return examBoardRegistrationService.listByStudent(student.getId()).stream()
                .map(r -> MyExamBoardRegistrationResponse.from(
                        r, gradeItemService.getExamBoardScore(r.getExamBoard().getId(), r.getProgramSubjectSectionEnrollment().getId())))
                .toList();
    }

    @PostMapping("/exam-board-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ExamBoardRegistrationResponse register(@Valid @RequestBody CreateExamBoardRegistrationRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        return ExamBoardRegistrationResponse.from(
                examBoardRegistrationService.registerProgramEnrollmentOfStudent(
                        student.getId(), request.examBoardId(), request.programSectionEnrollmentId()));
    }

    @PostMapping("/exam-board-registrations/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ExamBoardRegistration registration = examBoardRegistrationService.get(id);
        SubjectSectionEnrollment enrollment = registration.getProgramSubjectSectionEnrollment();
        if (enrollment == null || !enrollment.getStudent().getId().equals(student.getId())) {
            throw new ExamBoardRegistrationNotFoundException(id);
        }
        examBoardRegistrationService.withdraw(id);
    }
}

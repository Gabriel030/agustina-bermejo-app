package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CurriculumProgressResponse;
import com.red_escuela.backend.enrollment.usecase.curriculumprogress.ReadCurriculumProgressUseCase;
import com.red_escuela.backend.enrollment.usecase.curriculumprogress.ReadOwnCurriculumProgressUseCase;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurriculumProgressController {
    private final CurrentStudentService currentStudentService;
    private final ReadOwnCurriculumProgressUseCase readOwnCurriculumProgressUseCase;
    private final ReadCurriculumProgressUseCase readCurriculumProgressUseCase;

    public CurriculumProgressController(
            CurrentStudentService currentStudentService,
            ReadOwnCurriculumProgressUseCase readOwnCurriculumProgressUseCase,
            ReadCurriculumProgressUseCase readCurriculumProgressUseCase) {
        this.currentStudentService = currentStudentService;
        this.readOwnCurriculumProgressUseCase = readOwnCurriculumProgressUseCase;
        this.readCurriculumProgressUseCase = readCurriculumProgressUseCase;
    }

    @GetMapping("/me/program-enrollments/{id}/progress/subjects")
    public CurriculumProgressResponse myCurriculumProgress(@PathVariable Long id) {
        return readOwnCurriculumProgressUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/program-enrollments/{id}/progress/subjects")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CurriculumProgressResponse getCurriculumProgress(@PathVariable Long id) {
        return readCurriculumProgressUseCase.read(id);
    }
}

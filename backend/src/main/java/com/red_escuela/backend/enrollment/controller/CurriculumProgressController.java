package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CurriculumProgressResponse;
import com.red_escuela.backend.enrollment.dto.GraduationProjectionResponse;
import com.red_escuela.backend.enrollment.usecase.curriculumprogress.ReadCurriculumProgressUseCase;
import com.red_escuela.backend.enrollment.usecase.curriculumprogress.ReadGraduationProjectionUseCase;
import com.red_escuela.backend.enrollment.usecase.curriculumprogress.ReadOwnCurriculumProgressUseCase;
import com.red_escuela.backend.enrollment.usecase.curriculumprogress.ReadOwnGraduationProjectionUseCase;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurriculumProgressController {
    private final CurrentStudentService currentStudentService;
    private final ReadOwnCurriculumProgressUseCase readOwnCurriculumProgressUseCase;
    private final ReadCurriculumProgressUseCase readCurriculumProgressUseCase;
    private final ReadOwnGraduationProjectionUseCase readOwnGraduationProjectionUseCase;
    private final ReadGraduationProjectionUseCase readGraduationProjectionUseCase;

    public CurriculumProgressController(
            CurrentStudentService currentStudentService,
            ReadOwnCurriculumProgressUseCase readOwnCurriculumProgressUseCase,
            ReadCurriculumProgressUseCase readCurriculumProgressUseCase,
            ReadOwnGraduationProjectionUseCase readOwnGraduationProjectionUseCase,
            ReadGraduationProjectionUseCase readGraduationProjectionUseCase) {
        this.currentStudentService = currentStudentService;
        this.readOwnCurriculumProgressUseCase = readOwnCurriculumProgressUseCase;
        this.readCurriculumProgressUseCase = readCurriculumProgressUseCase;
        this.readOwnGraduationProjectionUseCase = readOwnGraduationProjectionUseCase;
        this.readGraduationProjectionUseCase = readGraduationProjectionUseCase;
    }

    @GetMapping("/me/program-enrollments/{id}/progress/subjects")
    public CurriculumProgressResponse myCurriculumProgress(@PathVariable Long id) {
        return readOwnCurriculumProgressUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/program-enrollments/{id}/progress/subjects")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CurriculumProgressResponse getCurriculumProgress(@PathVariable Long id) {
        return readCurriculumProgressUseCase.read(id);
    }

    @GetMapping("/me/program-enrollments/{id}/progress/projection")
    public GraduationProjectionResponse myGraduationProjection(@PathVariable Long id) {
        return readOwnGraduationProjectionUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/program-enrollments/{id}/progress/projection")
    @RequiresPermission(code = "enrollment.read_program_enrollments", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public GraduationProjectionResponse getGraduationProjection(@PathVariable Long id) {
        return readGraduationProjectionUseCase.read(id);
    }
}

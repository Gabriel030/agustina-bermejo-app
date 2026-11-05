package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetSectionGradeGridCommand;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetTeacherCourseSectionGradeGridUseCase;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetTeacherProgramSectionGradeGridUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeacherGradeGridController {

    private final GetTeacherProgramSectionGradeGridUseCase getTeacherProgramSectionGradeGridUseCase;
    private final GetTeacherCourseSectionGradeGridUseCase getTeacherCourseSectionGradeGridUseCase;
    private final CurrentInstructorService currentInstructorService;

    public TeacherGradeGridController(GetTeacherProgramSectionGradeGridUseCase getTeacherProgramSectionGradeGridUseCase,
                                      GetTeacherCourseSectionGradeGridUseCase getTeacherCourseSectionGradeGridUseCase,
                                      CurrentInstructorService currentInstructorService) {
        this.getTeacherProgramSectionGradeGridUseCase = getTeacherProgramSectionGradeGridUseCase;
        this.getTeacherCourseSectionGradeGridUseCase = getTeacherCourseSectionGradeGridUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @GetMapping("/program-sections/{id}/grade-grid")
    @PreAuthorize("hasRole('DOCENTE')")
    public TeacherGradeGridResponse getProgramSectionGradeGrid(@PathVariable Long id) {
        Long instructorId = currentInstructorService.resolveCurrentInstructor().getId();
        return getTeacherProgramSectionGradeGridUseCase.get(new GetSectionGradeGridCommand(id, instructorId));
    }

    @GetMapping("/course-sections/{id}/grade-grid")
    @PreAuthorize("hasRole('DOCENTE')")
    public TeacherGradeGridResponse getCourseSectionGradeGrid(@PathVariable Long id) {
        Long instructorId = currentInstructorService.resolveCurrentInstructor().getId();
        return getTeacherCourseSectionGradeGridUseCase.get(new GetSectionGradeGridCommand(id, instructorId));
    }
}

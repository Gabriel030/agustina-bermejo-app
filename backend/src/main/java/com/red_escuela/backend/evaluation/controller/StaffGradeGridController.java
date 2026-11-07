package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetStaffCourseSectionGradeGridUseCase;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetStaffProgramSectionGradeGridUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffGradeGridController {

    private final GetStaffProgramSectionGradeGridUseCase getStaffProgramSectionGradeGridUseCase;
    private final GetStaffCourseSectionGradeGridUseCase getStaffCourseSectionGradeGridUseCase;

    public StaffGradeGridController(GetStaffProgramSectionGradeGridUseCase getStaffProgramSectionGradeGridUseCase,
                                    GetStaffCourseSectionGradeGridUseCase getStaffCourseSectionGradeGridUseCase) {
        this.getStaffProgramSectionGradeGridUseCase = getStaffProgramSectionGradeGridUseCase;
        this.getStaffCourseSectionGradeGridUseCase = getStaffCourseSectionGradeGridUseCase;
    }

    @GetMapping("/staff/program-sections/{id}/grade-grid")
    @PreAuthorize("hasRole('DIRECTOR')")
    public TeacherGradeGridResponse getProgramSectionGradeGrid(@PathVariable Long id) {
        return getStaffProgramSectionGradeGridUseCase.get(id);
    }

    @GetMapping("/staff/course-sections/{id}/grade-grid")
    @PreAuthorize("hasRole('DIRECTOR')")
    public TeacherGradeGridResponse getCourseSectionGradeGrid(@PathVariable Long id) {
        return getStaffCourseSectionGradeGridUseCase.get(id);
    }
}

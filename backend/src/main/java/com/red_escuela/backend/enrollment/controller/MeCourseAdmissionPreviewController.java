package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CourseAdmissionPreviewResponse;
import com.red_escuela.backend.enrollment.usecase.courseenrollment.PreviewCourseAdmissionUseCase;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeCourseAdmissionPreviewController {
    private final CurrentStudentService currentStudentService;
    private final PreviewCourseAdmissionUseCase previewCourseAdmissionUseCase;

    public MeCourseAdmissionPreviewController(CurrentStudentService currentStudentService, PreviewCourseAdmissionUseCase previewCourseAdmissionUseCase) {
        this.currentStudentService = currentStudentService;
        this.previewCourseAdmissionUseCase = previewCourseAdmissionUseCase;
    }

    @GetMapping("/course-enrollment-preview")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "COURSE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "COURSE_ADMISSION_CHARGE_REJECTED, COURSE_ADMISSION_ALREADY_CHARGED")})
    public CourseAdmissionPreviewResponse previewCourseEnrollment(@RequestParam Long courseId) {
        Student student = currentStudentService.resolveCurrentStudent();
        return previewCourseAdmissionUseCase.preview(student.getId(), courseId);
    }
}

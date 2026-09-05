package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.service.CourseEnrollmentService;
import com.red_escuela.backend.service.CurrentStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Autoservicio del alumno sobre sus propios CourseEnrollment (Épica 2 Fase 1, Alumno · Inicio
 * y Mis cursos) — mismo patrón que MeEnrollmentController.myProgramEnrollments() para
 * ProgramEnrollment, que ya existía; a este le faltaba el equivalente de autoservicio (antes
 * solo /students/{id}/course-enrollments, de uso staff).
 */
@RestController
@RequestMapping("/me")
public class MeCourseEnrollmentController {

    private final CurrentStudentService currentStudentService;
    private final CourseEnrollmentService courseEnrollmentService;

    public MeCourseEnrollmentController(CurrentStudentService currentStudentService, CourseEnrollmentService courseEnrollmentService) {
        this.currentStudentService = currentStudentService;
        this.courseEnrollmentService = courseEnrollmentService;
    }

    @GetMapping("/course-enrollments")
    public List<CourseEnrollmentResponse> myCourses() {
        Student student = currentStudentService.resolveCurrentStudent();
        return courseEnrollmentService.listByStudent(student.getId()).stream().map(CourseEnrollmentResponse::from).toList();
    }
}

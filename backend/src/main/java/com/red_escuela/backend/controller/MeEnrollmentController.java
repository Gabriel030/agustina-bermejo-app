package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.ChangeSectionRequest;
import com.red_escuela.backend.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.dto.CreateOwnSectionEnrollmentRequest;
import com.red_escuela.backend.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.dto.ProgramProgressResponse;
import com.red_escuela.backend.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.service.CourseEnrollmentService;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.PersonRoleGranter;
import com.red_escuela.backend.service.ProgramEnrollmentService;
import com.red_escuela.backend.service.SubjectSectionEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Autoservicio del alumno (decisión 12): cualquier rol autenticado puede pegarle
 * (la autenticación ya la exige el filtro JWT global, sin @PreAuthorize adicional —
 * mismo criterio que PersonController). El Student siempre se resuelve del JWT vía
 * CurrentStudentService, nunca de un id de la URL.
 */
@RestController
@RequestMapping("/me")
public class MeEnrollmentController {

    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final PersonRoleGranter personRoleGranter;

    public MeEnrollmentController(
            CurrentStudentService currentStudentService,
            ProgramEnrollmentService programEnrollmentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            CourseEnrollmentService courseEnrollmentService,
            PersonRoleGranter personRoleGranter) {
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.personRoleGranter = personRoleGranter;
    }

    // Épica 7: inscripción a un curso con cupo, sin aprobación — el propio Alumno
    // la dispara (antes solo staff podía, ver CourseEnrollmentService.matricular).
    // Si todavía no tenía ningún rol (recién registrado), lo obtiene acá.
    @PostMapping("/course-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseEnrollmentResponse inscribirmeAUnCurso(@Valid @RequestBody CreateCourseEnrollmentRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        var enrollment = courseEnrollmentService.matricular(student.getId(), request);
        personRoleGranter.otorgarAlumnoSiNoLoTiene(student.getPerson());
        return CourseEnrollmentResponse.from(enrollment);
    }

    @GetMapping("/program-enrollments")
    public List<ProgramEnrollmentResponse> misMatriculaciones() {
        Student student = currentStudentService.resolveCurrentStudent();
        return programEnrollmentService.listarPorAlumno(student.getId()).stream()
                .map(ProgramEnrollmentResponse::from).toList();
    }

    @GetMapping("/program-enrollments/{id}/progress")
    public ProgramProgressResponse miProgreso(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment enrollment = programEnrollmentService.verDetalle(id);
        exigirPropietario(enrollment, student);
        return programEnrollmentService.calcularProgreso(id);
    }

    @PostMapping("/section-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionEnrollmentResponse inscribirme(@Valid @RequestBody CreateOwnSectionEnrollmentRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment programEnrollment = programEnrollmentService.verDetalle(request.programEnrollmentId());
        exigirPropietario(programEnrollment, student);

        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentService.inscribir(
                request.programEnrollmentId(), new CreateSectionEnrollmentRequest(request.sectionId()));
        return SectionEnrollmentResponse.from(enrollment);
    }

    @PostMapping("/section-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void darmeDeBaja(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        exigirPropietario(subjectSectionEnrollmentService.verDetalle(id), student);
        subjectSectionEnrollmentService.darDeBaja(id);
    }

    @PostMapping("/section-enrollments/{id}/change-section")
    public SectionEnrollmentResponse cambiarMiComision(@PathVariable Long id, @Valid @RequestBody ChangeSectionRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        exigirPropietario(subjectSectionEnrollmentService.verDetalle(id), student);
        return SectionEnrollmentResponse.from(subjectSectionEnrollmentService.cambiarComision(id, request));
    }

    @GetMapping("/section-enrollments")
    public List<SectionEnrollmentResponse> misInscripciones() {
        Student student = currentStudentService.resolveCurrentStudent();
        return subjectSectionEnrollmentService.listarPorAlumno(student.getId()).stream()
                .map(SectionEnrollmentResponse::from).toList();
    }

    // 404 (no 403) si el recurso no es del alumno autenticado: mismo criterio de no
    // confirmar por otra vía que el id existe, ya usado en PUT /me de Gestión de personas.
    private void exigirPropietario(ProgramEnrollment enrollment, Student student) {
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentNotFoundException(enrollment.getId());
        }
    }

    private void exigirPropietario(SubjectSectionEnrollment enrollment, Student student) {
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new SectionEnrollmentNotFoundException(enrollment.getId());
        }
    }
}

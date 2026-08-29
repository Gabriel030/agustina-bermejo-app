package com.red_escuela.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.dto.ChangeSectionRequest;
import com.red_escuela.backend.dto.CourseEnrollmentApplicationResponse;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.dto.CreateOwnSectionEnrollmentRequest;
import com.red_escuela.backend.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.dto.ProgramProgressResponse;
import com.red_escuela.backend.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.service.CourseEnrollmentApplicationService;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.ProgramEnrollmentService;
import com.red_escuela.backend.service.SubjectSectionEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
    private final CourseEnrollmentApplicationService courseEnrollmentApplicationService;
    private final ObjectMapper objectMapper;

    public MeEnrollmentController(
            CurrentStudentService currentStudentService,
            ProgramEnrollmentService programEnrollmentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            CourseEnrollmentApplicationService courseEnrollmentApplicationService,
            ObjectMapper objectMapper) {
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.courseEnrollmentApplicationService = courseEnrollmentApplicationService;
        this.objectMapper = objectMapper;
    }

    // Épica 7 Fase 3: ya NO matricula en el momento — el pago de la primera cuota es
    // un paso bloqueante (ver spec técnica), la matriculación real recién ocurre
    // cuando el comprobante se aprueba (PaymentReceiptService.aprobar). Multipart:
    // sectionOverrides viaja como JSON string (`{"12":5,"13":9}`) porque no hay una
    // forma razonable de mandar un Map en multipart sin agregar infra nueva.
    @PostMapping(value = "/course-enrollments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CourseEnrollmentApplicationResponse inscribirmeAUnCurso(
            @RequestParam Long courseId,
            @RequestParam(required = false) String sectionOverrides,
            @RequestPart MultipartFile comprobante) {
        Student student = currentStudentService.resolveCurrentStudent();
        CreateCourseEnrollmentRequest request = new CreateCourseEnrollmentRequest(courseId, deserializarOverrides(sectionOverrides));
        CourseEnrollmentApplication application = courseEnrollmentApplicationService.solicitarConPago(student.getId(), request, comprobante);
        return CourseEnrollmentApplicationResponse.from(application);
    }

    @GetMapping("/course-enrollment-applications")
    public List<CourseEnrollmentApplicationResponse> misSolicitudesDeCurso() {
        Student student = currentStudentService.resolveCurrentStudent();
        return courseEnrollmentApplicationService.listarPropias(student.getId()).stream()
                .map(CourseEnrollmentApplicationResponse::from).toList();
    }

    private Map<Long, Long> deserializarOverrides(String sectionOverridesJson) {
        if (sectionOverridesJson == null || sectionOverridesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(sectionOverridesJson, new TypeReference<Map<Long, Long>>() {
            });
        } catch (JsonProcessingException e) {
            throw new InvalidSectionOverridesException(sectionOverridesJson, e);
        }
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
                .map(enrollment -> SectionEnrollmentResponse.from(enrollment, subjectSectionEnrollmentService.resolverAnioCurricular(enrollment)))
                .toList();
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

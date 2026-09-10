package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.entity.AttendanceChangeRequest;
import com.red_escuela.backend.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.service.AttendanceChangeRequestService;
import com.red_escuela.backend.service.CurrentInstructorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AttendanceChangeRequestController {

    private final AttendanceChangeRequestService attendanceChangeRequestService;
    private final CurrentInstructorService currentInstructorService;

    public AttendanceChangeRequestController(
            AttendanceChangeRequestService attendanceChangeRequestService, CurrentInstructorService currentInstructorService) {
        this.attendanceChangeRequestService = attendanceChangeRequestService;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/attendance/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public AttendanceChangeRequestResponse crear(@PathVariable Long id, @Valid @RequestBody CreateAttendanceChangeRequestRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        AttendanceChangeRequest created = attendanceChangeRequestService.crear(id, instructor, request);
        return toResponse(created);
    }

    @GetMapping("/attendance/change-requests")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<AttendanceChangeRequestResponse> listarPendientes(
            @RequestParam(defaultValue = "PENDING") AttendanceChangeRequestStatus status) {
        return attendanceChangeRequestService.listarPorEstado(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/me/attendance-change-requests")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceChangeRequestResponse> misSolicitudes() {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceChangeRequestService.listarPropias(instructor.getId()).stream().map(this::toResponse).toList();
    }

    @PostMapping("/attendance/change-requests/{id}/approve")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceChangeRequestResponse aprobar(@PathVariable Long id) {
        return toResponse(attendanceChangeRequestService.aprobar(id));
    }

    @PostMapping("/attendance/change-requests/{id}/reject")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceChangeRequestResponse rechazar(@PathVariable Long id) {
        return toResponse(attendanceChangeRequestService.rechazar(id));
    }

    private AttendanceChangeRequestResponse toResponse(AttendanceChangeRequest request) {
        var attendance = request.getAttendance();
        Person studentPerson;
        String subjectName;
        String sectionName;
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            var enrollment = attendance.getProgramSubjectSectionEnrollment();
            studentPerson = enrollment.getStudent().getPerson();
            subjectName = enrollment.getSubjectSection().getSubject().getName();
            sectionName = enrollment.getSubjectSection().getName();
        } else {
            var enrollment = attendance.getCourseSubjectSectionEnrollment();
            studentPerson = enrollment.getStudent().getPerson();
            subjectName = enrollment.getCourseSubjectSection().getCourseSubject().getName();
            sectionName = enrollment.getCourseSubjectSection().getName();
        }
        Person requestedByPerson = request.getRequestedBy().getPerson();
        return AttendanceChangeRequestResponse.from(
                request, nombreCompleto(requestedByPerson), subjectName, sectionName,
                attendance.getClassDate(), nombreCompleto(studentPerson));
    }

    private String nombreCompleto(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}

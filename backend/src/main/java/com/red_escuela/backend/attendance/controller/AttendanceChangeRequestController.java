package com.red_escuela.backend.attendance.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.ApproveAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.ListAttendanceChangeRequestsByStatusUseCase;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.ListMyAttendanceChangeRequestsUseCase;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RejectAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RequestAttendanceChangeCommand;
import com.red_escuela.backend.attendance.usecase.attendancechangerequest.RequestAttendanceChangeUseCase;
import com.red_escuela.backend.identity.entity.Person;
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

    private final RequestAttendanceChangeUseCase requestAttendanceChangeUseCase;
    private final ListAttendanceChangeRequestsByStatusUseCase listAttendanceChangeRequestsByStatusUseCase;
    private final ListMyAttendanceChangeRequestsUseCase listMyAttendanceChangeRequestsUseCase;
    private final ApproveAttendanceChangeRequestUseCase approveAttendanceChangeRequestUseCase;
    private final RejectAttendanceChangeRequestUseCase rejectAttendanceChangeRequestUseCase;
    private final CurrentInstructorService currentInstructorService;

    public AttendanceChangeRequestController(
            RequestAttendanceChangeUseCase requestAttendanceChangeUseCase,
            ListAttendanceChangeRequestsByStatusUseCase listAttendanceChangeRequestsByStatusUseCase,
            ListMyAttendanceChangeRequestsUseCase listMyAttendanceChangeRequestsUseCase,
            ApproveAttendanceChangeRequestUseCase approveAttendanceChangeRequestUseCase,
            RejectAttendanceChangeRequestUseCase rejectAttendanceChangeRequestUseCase,
            CurrentInstructorService currentInstructorService) {
        this.requestAttendanceChangeUseCase = requestAttendanceChangeUseCase;
        this.listAttendanceChangeRequestsByStatusUseCase = listAttendanceChangeRequestsByStatusUseCase;
        this.listMyAttendanceChangeRequestsUseCase = listMyAttendanceChangeRequestsUseCase;
        this.approveAttendanceChangeRequestUseCase = approveAttendanceChangeRequestUseCase;
        this.rejectAttendanceChangeRequestUseCase = rejectAttendanceChangeRequestUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/attendance/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public AttendanceChangeRequestResponse create(@PathVariable Long id, @Valid @RequestBody CreateAttendanceChangeRequestRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        AttendanceChangeRequest created = requestAttendanceChangeUseCase.request(
                new RequestAttendanceChangeCommand(id, instructor, request));
        return toResponse(created);
    }

    @GetMapping("/attendance/change-requests")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<AttendanceChangeRequestResponse> listByStatus(
            @RequestParam(defaultValue = "PENDING") AttendanceChangeRequestStatus status) {
        return listAttendanceChangeRequestsByStatusUseCase.list(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/me/attendance-change-requests")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceChangeRequestResponse> mine() {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return listMyAttendanceChangeRequestsUseCase.list(instructor.getId()).stream().map(this::toResponse).toList();
    }

    @PostMapping("/attendance/change-requests/{id}/approve")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceChangeRequestResponse approve(@PathVariable Long id) {
        return toResponse(approveAttendanceChangeRequestUseCase.approve(id));
    }

    @PostMapping("/attendance/change-requests/{id}/reject")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceChangeRequestResponse reject(@PathVariable Long id) {
        return toResponse(rejectAttendanceChangeRequestUseCase.reject(id));
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
                request, fullName(requestedByPerson), subjectName, sectionName,
                attendance.getClassDate(), fullName(studentPerson));
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}

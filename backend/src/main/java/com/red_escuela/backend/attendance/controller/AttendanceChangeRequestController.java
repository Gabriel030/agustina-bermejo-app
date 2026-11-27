package com.red_escuela.backend.attendance.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestResult;
import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.dto.RejectAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.dto.RevertAttendanceChangeRequestDecisionRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.usecase.changerequest.ApproveAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.ListAttendanceChangeRequestsByStatusUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.ListMyAttendanceChangeRequestsUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.ListResolvedAttendanceChangeRequestsCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.ListResolvedAttendanceChangeRequestsUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.RejectAttendanceChangeRequestCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.RejectAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.RequestAttendanceChangeCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.RequestAttendanceChangeUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.RevertAttendanceChangeRequestDecisionCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.RevertAttendanceChangeRequestDecisionUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final RevertAttendanceChangeRequestDecisionUseCase revertAttendanceChangeRequestDecisionUseCase;
    private final ListResolvedAttendanceChangeRequestsUseCase listResolvedAttendanceChangeRequestsUseCase;
    private final CurrentInstructorService currentInstructorService;

    public AttendanceChangeRequestController(
            RequestAttendanceChangeUseCase requestAttendanceChangeUseCase,
            ListAttendanceChangeRequestsByStatusUseCase listAttendanceChangeRequestsByStatusUseCase,
            ListMyAttendanceChangeRequestsUseCase listMyAttendanceChangeRequestsUseCase,
            ApproveAttendanceChangeRequestUseCase approveAttendanceChangeRequestUseCase,
            RejectAttendanceChangeRequestUseCase rejectAttendanceChangeRequestUseCase,
            RevertAttendanceChangeRequestDecisionUseCase revertAttendanceChangeRequestDecisionUseCase,
            ListResolvedAttendanceChangeRequestsUseCase listResolvedAttendanceChangeRequestsUseCase,
            CurrentInstructorService currentInstructorService) {
        this.requestAttendanceChangeUseCase = requestAttendanceChangeUseCase;
        this.listAttendanceChangeRequestsByStatusUseCase = listAttendanceChangeRequestsByStatusUseCase;
        this.listMyAttendanceChangeRequestsUseCase = listMyAttendanceChangeRequestsUseCase;
        this.approveAttendanceChangeRequestUseCase = approveAttendanceChangeRequestUseCase;
        this.rejectAttendanceChangeRequestUseCase = rejectAttendanceChangeRequestUseCase;
        this.revertAttendanceChangeRequestDecisionUseCase = revertAttendanceChangeRequestDecisionUseCase;
        this.listResolvedAttendanceChangeRequestsUseCase = listResolvedAttendanceChangeRequestsUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/attendance/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "attendance.request_change", legacyRoles = "ROLE_DOCENTE")
    public AttendanceChangeRequestResponse create(@PathVariable Long id, @Valid @RequestBody CreateAttendanceChangeRequestRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        AttendanceChangeRequest created = requestAttendanceChangeUseCase.request(
                new RequestAttendanceChangeCommand(id, instructor.getId(), request));
        return toResponse(created);
    }

    @GetMapping("/attendance/change-requests")
    @RequiresPermission(code = "attendance.read_change_requests", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public List<AttendanceChangeRequestResponse> listByStatus(
            @RequestParam(defaultValue = "PENDING") AttendanceChangeRequestStatus status) {
        return listAttendanceChangeRequestsByStatusUseCase.list(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/attendance/change-requests/resolved")
    @RequiresPermission(code = "attendance.read_change_requests", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public PageResponse<AttendanceChangeRequestResponse> listResolved(
            @RequestParam(required = false) AttendanceChangeRequestResult result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listResolvedAttendanceChangeRequestsUseCase.list(new ListResolvedAttendanceChangeRequestsCommand(result, page, size));
    }

    @GetMapping("/me/attendance-change-requests")
    @RequiresPermission(code = "attendance.request_change", legacyRoles = "ROLE_DOCENTE")
    public List<AttendanceChangeRequestResponse> mine() {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return listMyAttendanceChangeRequestsUseCase.list(instructor.getId()).stream().map(this::toResponse).toList();
    }

    @PostMapping("/attendance/change-requests/{id}/approve")
    @RequiresPermission(code = "attendance.review_change_request", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public AttendanceChangeRequestResponse approve(@PathVariable Long id) {
        return toResponse(approveAttendanceChangeRequestUseCase.approve(id));
    }

    @PostMapping("/attendance/change-requests/{id}/reject")
    @RequiresPermission(code = "attendance.review_change_request", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public AttendanceChangeRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectAttendanceChangeRequestRequest request) {
        return toResponse(rejectAttendanceChangeRequestUseCase.reject(new RejectAttendanceChangeRequestCommand(id, request.reason())));
    }

    @PostMapping("/attendance/change-requests/{id}/revert")
    @RequiresPermission(code = "attendance.review_change_request", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public AttendanceChangeRequestResponse revert(@PathVariable Long id, @Valid @RequestBody RevertAttendanceChangeRequestDecisionRequest request) {
        return revertAttendanceChangeRequestDecisionUseCase.revert(new RevertAttendanceChangeRequestDecisionCommand(id, request.reason()));
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
        Person decidedByPerson = request.getApprovedBy();
        return AttendanceChangeRequestResponse.from(
                request, fullName(requestedByPerson), subjectName, sectionName,
                attendance.getClassDate(), fullName(studentPerson),
                decidedByPerson == null ? null : fullName(decidedByPerson));
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}

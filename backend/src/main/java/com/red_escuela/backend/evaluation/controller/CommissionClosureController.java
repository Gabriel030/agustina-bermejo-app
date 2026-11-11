package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ApproveClosureRequest;
import com.red_escuela.backend.evaluation.dto.ClosurePrerequisiteResponse;
import com.red_escuela.backend.evaluation.dto.ClosureReasonRequest;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.dto.TeacherObservationRequest;
import com.red_escuela.backend.evaluation.usecase.closure.ApproveClosureCommand;
import com.red_escuela.backend.evaluation.usecase.closure.ApproveClosureUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.GrantClosureExceptionCommand;
import com.red_escuela.backend.evaluation.usecase.closure.GrantClosureExceptionUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.ReopenClosureCommand;
import com.red_escuela.backend.evaluation.usecase.closure.ReopenClosureUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.RevokeClosureExceptionCommand;
import com.red_escuela.backend.evaluation.usecase.closure.RevokeClosureExceptionUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.ConfirmClosureCommand;
import com.red_escuela.backend.evaluation.usecase.closure.ConfirmClosureUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.GetClosureSheetCommand;
import com.red_escuela.backend.evaluation.usecase.closure.GetClosureSheetUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.GetClosureStudentPrerequisitesCommand;
import com.red_escuela.backend.evaluation.usecase.closure.GetClosureStudentPrerequisitesUseCase;
import com.red_escuela.backend.evaluation.usecase.closure.RecordClosureObservationCommand;
import com.red_escuela.backend.evaluation.usecase.closure.RecordClosureObservationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
public class CommissionClosureController {

    private static final Set<String> STAFF_AUTHORITIES = Set.of("ROLE_PRECEPTOR", "ROLE_ADMINISTRATIVO", "ROLE_DIRECTOR");

    private final GetClosureSheetUseCase getClosureSheetUseCase;
    private final ConfirmClosureUseCase confirmClosureUseCase;
    private final RecordClosureObservationUseCase recordClosureObservationUseCase;
    private final GetClosureStudentPrerequisitesUseCase getClosureStudentPrerequisitesUseCase;
    private final ApproveClosureUseCase approveClosureUseCase;
    private final ReopenClosureUseCase reopenClosureUseCase;
    private final GrantClosureExceptionUseCase grantClosureExceptionUseCase;
    private final RevokeClosureExceptionUseCase revokeClosureExceptionUseCase;
    private final CurrentInstructorService currentInstructorService;

    public CommissionClosureController(GetClosureSheetUseCase getClosureSheetUseCase,
            ConfirmClosureUseCase confirmClosureUseCase, RecordClosureObservationUseCase recordClosureObservationUseCase,
            GetClosureStudentPrerequisitesUseCase getClosureStudentPrerequisitesUseCase,
            ApproveClosureUseCase approveClosureUseCase, ReopenClosureUseCase reopenClosureUseCase,
            GrantClosureExceptionUseCase grantClosureExceptionUseCase,
            RevokeClosureExceptionUseCase revokeClosureExceptionUseCase, CurrentInstructorService currentInstructorService) {
        this.getClosureSheetUseCase = getClosureSheetUseCase;
        this.confirmClosureUseCase = confirmClosureUseCase;
        this.recordClosureObservationUseCase = recordClosureObservationUseCase;
        this.getClosureStudentPrerequisitesUseCase = getClosureStudentPrerequisitesUseCase;
        this.approveClosureUseCase = approveClosureUseCase;
        this.reopenClosureUseCase = reopenClosureUseCase;
        this.grantClosureExceptionUseCase = grantClosureExceptionUseCase;
        this.revokeClosureExceptionUseCase = revokeClosureExceptionUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @GetMapping("/program-sections/{id}/closure-sheet")
    @PreAuthorize("hasAnyRole('DOCENTE', 'PRECEPTOR', 'ADMINISTRATIVO', 'DIRECTOR')")
    public ClosureSheetResponse getProgramSectionClosureSheet(@PathVariable Long id, Authentication authentication) {
        return readSheet(ClosureSectionKind.PROGRAM, id, authentication);
    }

    @GetMapping("/course-sections/{id}/closure-sheet")
    @PreAuthorize("hasAnyRole('DOCENTE', 'PRECEPTOR', 'ADMINISTRATIVO', 'DIRECTOR')")
    public ClosureSheetResponse getCourseSectionClosureSheet(@PathVariable Long id, Authentication authentication) {
        return readSheet(ClosureSectionKind.COURSE, id, authentication);
    }

    @PostMapping("/program-sections/{id}/closure/confirm")
    @PreAuthorize("hasRole('DOCENTE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmProgramSectionClosure(@PathVariable Long id) {
        confirmClosureUseCase.confirm(new ConfirmClosureCommand(ClosureSectionKind.PROGRAM, id, currentInstructorId()));
    }

    @PostMapping("/course-sections/{id}/closure/confirm")
    @PreAuthorize("hasRole('DOCENTE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmCourseSectionClosure(@PathVariable Long id) {
        confirmClosureUseCase.confirm(new ConfirmClosureCommand(ClosureSectionKind.COURSE, id, currentInstructorId()));
    }

    @PutMapping("/program-sections/{id}/closure/students/{enrollmentId}/observation")
    @PreAuthorize("hasRole('DOCENTE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordProgramSectionObservation(@PathVariable Long id, @PathVariable Long enrollmentId,
            @Valid @RequestBody TeacherObservationRequest request) {
        recordClosureObservationUseCase.record(new RecordClosureObservationCommand(
                ClosureSectionKind.PROGRAM, id, enrollmentId, currentInstructorId(), request.observation()));
    }

    @PutMapping("/course-sections/{id}/closure/students/{enrollmentId}/observation")
    @PreAuthorize("hasRole('DOCENTE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordCourseSectionObservation(@PathVariable Long id, @PathVariable Long enrollmentId,
            @Valid @RequestBody TeacherObservationRequest request) {
        recordClosureObservationUseCase.record(new RecordClosureObservationCommand(
                ClosureSectionKind.COURSE, id, enrollmentId, currentInstructorId(), request.observation()));
    }

    @PostMapping("/program-sections/{id}/closure/approve")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveProgramSectionClosure(@PathVariable Long id, @Valid @RequestBody ApproveClosureRequest request) {
        approveClosureUseCase.approve(new ApproveClosureCommand(ClosureSectionKind.PROGRAM, id, request.book(), request.folio(),
                request.closeWithoutTeacherConfirmation()));
    }

    @PostMapping("/program-sections/{id}/closure/reopen")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenProgramSectionClosure(@PathVariable Long id, @Valid @RequestBody ClosureReasonRequest request) {
        reopenClosureUseCase.reopen(new ReopenClosureCommand(ClosureSectionKind.PROGRAM, id, request.reason()));
    }

    @PutMapping("/program-sections/{id}/closure/students/{enrollmentId}/exception")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantProgramSectionException(@PathVariable Long id, @PathVariable Long enrollmentId,
            @Valid @RequestBody ClosureReasonRequest request) {
        grantClosureExceptionUseCase.grant(new GrantClosureExceptionCommand(
                ClosureSectionKind.PROGRAM, id, enrollmentId, request.reason()));
    }

    @DeleteMapping("/program-sections/{id}/closure/students/{enrollmentId}/exception")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeProgramSectionException(@PathVariable Long id, @PathVariable Long enrollmentId) {
        revokeClosureExceptionUseCase.revoke(new RevokeClosureExceptionCommand(ClosureSectionKind.PROGRAM, id, enrollmentId));
    }

    @PostMapping("/course-sections/{id}/closure/approve")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveCourseSectionClosure(@PathVariable Long id, @Valid @RequestBody ApproveClosureRequest request) {
        approveClosureUseCase.approve(new ApproveClosureCommand(ClosureSectionKind.COURSE, id, request.book(), request.folio(),
                request.closeWithoutTeacherConfirmation()));
    }

    @PostMapping("/course-sections/{id}/closure/reopen")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenCourseSectionClosure(@PathVariable Long id, @Valid @RequestBody ClosureReasonRequest request) {
        reopenClosureUseCase.reopen(new ReopenClosureCommand(ClosureSectionKind.COURSE, id, request.reason()));
    }

    @PutMapping("/course-sections/{id}/closure/students/{enrollmentId}/exception")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantCourseSectionException(@PathVariable Long id, @PathVariable Long enrollmentId,
            @Valid @RequestBody ClosureReasonRequest request) {
        grantClosureExceptionUseCase.grant(new GrantClosureExceptionCommand(
                ClosureSectionKind.COURSE, id, enrollmentId, request.reason()));
    }

    @DeleteMapping("/course-sections/{id}/closure/students/{enrollmentId}/exception")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeCourseSectionException(@PathVariable Long id, @PathVariable Long enrollmentId) {
        revokeClosureExceptionUseCase.revoke(new RevokeClosureExceptionCommand(ClosureSectionKind.COURSE, id, enrollmentId));
    }

    @GetMapping("/commission-closures/{closureId}/students/{enrollmentId}/prerequisites")
    @PreAuthorize("hasAnyRole('DOCENTE', 'PRECEPTOR', 'ADMINISTRATIVO', 'DIRECTOR')")
    public List<ClosurePrerequisiteResponse> getStudentPrerequisites(@PathVariable Long closureId,
            @PathVariable Long enrollmentId, Authentication authentication) {
        ClosureViewerProfile viewer = viewerOf(authentication);
        return getClosureStudentPrerequisitesUseCase.get(new GetClosureStudentPrerequisitesCommand(
                closureId, enrollmentId, viewer, instructorIdFor(viewer)));
    }

    private ClosureSheetResponse readSheet(ClosureSectionKind kind, Long sectionId, Authentication authentication) {
        ClosureViewerProfile viewer = viewerOf(authentication);
        return getClosureSheetUseCase.get(new GetClosureSheetCommand(kind, sectionId, viewer, instructorIdFor(viewer)));
    }

    private ClosureViewerProfile viewerOf(Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(authority -> STAFF_AUTHORITIES.contains(authority.getAuthority()));
        return staff ? ClosureViewerProfile.STAFF : ClosureViewerProfile.TEACHER;
    }

    private Long instructorIdFor(ClosureViewerProfile viewer) {
        return viewer == ClosureViewerProfile.TEACHER ? currentInstructorId() : null;
    }

    private Long currentInstructorId() {
        return currentInstructorService.resolveCurrentInstructor().getId();
    }
}

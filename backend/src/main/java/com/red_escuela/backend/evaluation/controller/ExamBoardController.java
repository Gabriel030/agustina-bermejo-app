package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.AssignTribunalRequest;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.service.ExamBoardService;
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
import java.util.Set;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.service.CurrentInstructorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@RestController
public class ExamBoardController {

    private static final Set<String> STAFF_WITH_FULL_ACCESS = Set.of("ROLE_PRECEPTOR", "ROLE_ADMINISTRATIVO", "ROLE_DIRECTOR");

    private final ExamBoardService examBoardService;
    private final CurrentInstructorService currentInstructorService;

    public ExamBoardController(ExamBoardService examBoardService, CurrentInstructorService currentInstructorService) {
        this.examBoardService = examBoardService;
        this.currentInstructorService = currentInstructorService;
    }

    private boolean isTeacherOnly(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(STAFF_WITH_FULL_ACCESS::contains);
    }

    private void requireTribunalMemberIfTeacher(Long examBoardId, Authentication authentication) {
        if (isTeacherOnly(authentication)) {
            examBoardService.requireTribunalMember(examBoardId, currentInstructorService.resolveCurrentInstructor().getId());
        }
    }

    @PostMapping("/exam-boards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ExamBoardResponse create(@Valid @RequestBody CreateExamBoardRequest request) {
        return ExamBoardResponse.from(examBoardService.create(request));
    }

    @GetMapping("/exam-boards")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public List<ExamBoardResponse> list(@RequestParam(required = false) ExamBoardStatus status, Authentication authentication) {
        List<ExamBoard> examBoards = isTeacherOnly(authentication)
                ? examBoardService.listForTribunalMember(currentInstructorService.resolveCurrentInstructor().getId(), status)
                : examBoardService.list(status);
        return examBoards.stream().map(ExamBoardResponse::from).toList();
    }

    @GetMapping("/exam-boards/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public ExamBoardResponse get(@PathVariable Long id, Authentication authentication) {
        requireTribunalMemberIfTeacher(id, authentication);
        return ExamBoardResponse.from(examBoardService.get(id));
    }

    @PostMapping("/exam-boards/{id}/tribunal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void assignTribunal(@PathVariable Long id, @Valid @RequestBody AssignTribunalRequest request) {
        examBoardService.assignTribunal(id, request.instructorId());
    }

    @PostMapping("/exam-boards/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void close(@PathVariable Long id) {
        examBoardService.close(id);
    }

    @GetMapping("/exam-boards/{id}/registrations")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public List<ExamBoardRegistrationSummaryResponse> listRegistrants(@PathVariable Long id, Authentication authentication) {
        requireTribunalMemberIfTeacher(id, authentication);
        return examBoardService.listRegistrantsWithDetail(id);
    }
}

package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AssignExaminersRequest;
import com.red_escuela.backend.dto.CreateExamBoardRequest;
import com.red_escuela.backend.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.dto.ExamBoardResponse;
import com.red_escuela.backend.entity.ExamBoardStatus;
import com.red_escuela.backend.service.ExamBoardService;
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

/** RF-PR-09/10: creación, tribunal y cierre de acta de mesas de examen. */
@RestController
public class ExamBoardController {

    private final ExamBoardService examBoardService;

    public ExamBoardController(ExamBoardService examBoardService) {
        this.examBoardService = examBoardService;
    }

    @PostMapping("/exam-boards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ExamBoardResponse create(@Valid @RequestBody CreateExamBoardRequest request) {
        return ExamBoardResponse.from(examBoardService.create(request));
    }

    @GetMapping("/exam-boards")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public List<ExamBoardResponse> list(@RequestParam(required = false) ExamBoardStatus status) {
        return examBoardService.list(status).stream().map(ExamBoardResponse::from).toList();
    }

    @GetMapping("/exam-boards/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public ExamBoardResponse viewDetail(@PathVariable Long id) {
        return ExamBoardResponse.from(examBoardService.viewDetail(id));
    }

    @PostMapping("/exam-boards/{id}/tribunal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void assignExaminers(@PathVariable Long id, @Valid @RequestBody AssignExaminersRequest request) {
        examBoardService.assignExaminers(id, request.instructorId());
    }

    @PostMapping("/exam-boards/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void closeRecord(@PathVariable Long id) {
        examBoardService.closeRecord(id);
    }

    @GetMapping("/exam-boards/{id}/registrations")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public List<ExamBoardRegistrationSummaryResponse> listRegistrations(@PathVariable Long id) {
        return examBoardService.listRegistrationsWithDetail(id);
    }
}

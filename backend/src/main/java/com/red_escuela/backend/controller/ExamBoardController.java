package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AssignTribunalRequest;
import com.red_escuela.backend.dto.CreateExamBoardRequest;
import com.red_escuela.backend.dto.ExamBoardRegistrationResponse;
import com.red_escuela.backend.dto.ExamBoardResponse;
import com.red_escuela.backend.service.ExamBoardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ExamBoardResponse crear(@Valid @RequestBody CreateExamBoardRequest request) {
        return ExamBoardResponse.from(examBoardService.crear(request));
    }

    @PostMapping("/exam-boards/{id}/tribunal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void asignarTribunal(@PathVariable Long id, @Valid @RequestBody AssignTribunalRequest request) {
        examBoardService.asignarTribunal(id, request.instructorId());
    }

    @PostMapping("/exam-boards/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void cerrarActa(@PathVariable Long id) {
        examBoardService.cerrarActa(id);
    }

    @GetMapping("/exam-boards/{id}/registrations")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public List<ExamBoardRegistrationResponse> listarAnotados(@PathVariable Long id) {
        return examBoardService.listarAnotados(id).stream().map(ExamBoardRegistrationResponse::from).toList();
    }
}

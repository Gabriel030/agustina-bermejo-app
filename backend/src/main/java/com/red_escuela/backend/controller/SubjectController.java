package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateSubjectRequest;
import com.red_escuela.backend.dto.SubjectResponse;
import com.red_escuela.backend.dto.UpdateSubjectRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public SubjectResponse createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        return SubjectResponse.from(subjectService.createSubject(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public SubjectResponse updateSubject(@PathVariable Long id, @RequestBody UpdateSubjectRequest request) {
        return SubjectResponse.from(subjectService.updateSubject(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        subjectService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        subjectService.reactivate(id);
    }

    @GetMapping
    public List<SubjectResponse> list(@RequestParam(required = false) StatusType status) {
        return subjectService.list(status).stream().map(SubjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SubjectResponse verDetalle(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.verDetalle(id));
    }
}

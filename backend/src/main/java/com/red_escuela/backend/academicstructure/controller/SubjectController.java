package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.SubjectResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.service.SubjectService;
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
    public SubjectResponse create(@Valid @RequestBody CreateSubjectRequest request) {
        return SubjectResponse.from(subjectService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public SubjectResponse update(@PathVariable Long id, @RequestBody UpdateSubjectRequest request) {
        return SubjectResponse.from(subjectService.update(id, request));
    }

    @PostMapping("/{id}/submit-for-approval")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public SubjectResponse submitForApproval(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('DIRECTOR')")
    public SubjectResponse approve(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('DIRECTOR')")
    public SubjectResponse reject(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.reject(id));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void deactivate(@PathVariable Long id) {
        subjectService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void reactivate(@PathVariable Long id) {
        subjectService.reactivate(id);
    }

    @GetMapping
    public List<SubjectResponse> list(@RequestParam(required = false) SubjectStatus status) {
        return subjectService.list(status).stream().map(SubjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.get(id));
    }
}

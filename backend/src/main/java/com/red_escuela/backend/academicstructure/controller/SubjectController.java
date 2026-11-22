package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.RejectSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.SubjectResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "academicstructure.manage_subjects", legacyRoles = "ROLE_ADMINISTRATIVO")
    public SubjectResponse create(@Valid @RequestBody CreateSubjectRequest request) {
        return SubjectResponse.from(subjectService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(code = "academicstructure.manage_subjects", legacyRoles = "ROLE_ADMINISTRATIVO")
    public SubjectResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSubjectRequest request) {
        return SubjectResponse.from(subjectService.update(id, request));
    }

    @PostMapping("/{id}/submit-for-approval")
    @RequiresPermission(code = "academicstructure.manage_subjects", legacyRoles = "ROLE_ADMINISTRATIVO")
    public SubjectResponse submitForApproval(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission(code = "academicstructure.review_subject", legacyRoles = "ROLE_DIRECTOR")
    public SubjectResponse approve(@PathVariable Long id) {
        return SubjectResponse.from(subjectService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission(code = "academicstructure.review_subject", legacyRoles = "ROLE_DIRECTOR")
    public SubjectResponse reject(@PathVariable Long id, @Valid @RequestBody RejectSubjectRequest request) {
        return SubjectResponse.from(subjectService.reject(id, request.reason()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_subject_status", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public void deactivate(@PathVariable Long id) {
        subjectService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_subject_status", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
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

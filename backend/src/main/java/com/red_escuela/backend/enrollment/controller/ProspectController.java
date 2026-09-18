package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.service.ProspectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/prospects")
public class ProspectController {

    private final ProspectService prospectService;

    public ProspectController(ProspectService prospectService) {
        this.prospectService = prospectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createProspect(@Valid @RequestBody CreateProspectRequest request) {
        return StudentResponse.from(prospectService.createProspect(request));
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public StudentDocumentResponse addDocument(
            @PathVariable Long id,
            @RequestParam DocumentType type,
            @RequestPart MultipartFile file) {
        return StudentDocumentResponse.from(prospectService.addDocument(id, type, file));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<StudentDocumentResponse> listDocuments(@PathVariable Long id) {
        return prospectService.listDocuments(id).stream().map(StudentDocumentResponse::from).toList();
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public StudentResponse confirm(@PathVariable Long id, @Valid @RequestBody ConfirmProspectRequest request) {
        return StudentResponse.from(prospectService.confirm(id, request));
    }
}

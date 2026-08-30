package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.dto.EvaluationSchemeResponse;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.service.EvaluationSchemeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EvaluationSchemeController {

    private final EvaluationSchemeService evaluationSchemeService;

    public EvaluationSchemeController(EvaluationSchemeService evaluationSchemeService) {
        this.evaluationSchemeService = evaluationSchemeService;
    }

    @GetMapping("/evaluation-schemes")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<EvaluationSchemeResponse> list() {
        return evaluationSchemeService.list().stream()
                .map(scheme -> EvaluationSchemeResponse.from(scheme, evaluationSchemeService.isInUse(scheme.getId())))
                .toList();
    }

    @PostMapping("/evaluation-schemes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public EvaluationSchemeResponse create(@Valid @RequestBody CreateEvaluationSchemeRequest request) {
        EvaluationScheme scheme = evaluationSchemeService.create(request);
        return EvaluationSchemeResponse.from(scheme, false);
    }

    @PutMapping("/evaluation-schemes/{id}")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public EvaluationSchemeResponse update(@PathVariable Long id, @Valid @RequestBody CreateEvaluationSchemeRequest request) {
        EvaluationScheme scheme = evaluationSchemeService.update(id, request);
        return EvaluationSchemeResponse.from(scheme, false);
    }
}

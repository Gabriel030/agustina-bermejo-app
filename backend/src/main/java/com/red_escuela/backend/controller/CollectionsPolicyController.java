package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CollectionsPolicyResponse;
import com.red_escuela.backend.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.service.CollectionsPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// La política de mora es una decisión del Director (decisión 2026-09-03, Épica 8 reconciliada) —
// Administrativo solo puede leerla, no cargarla.
@RestController
public class CollectionsPolicyController {

    private final CollectionsPolicyService collectionsPolicyService;

    public CollectionsPolicyController(CollectionsPolicyService collectionsPolicyService) {
        this.collectionsPolicyService = collectionsPolicyService;
    }

    @PostMapping("/collections-policy")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public CollectionsPolicyResponse create(@Valid @RequestBody CreateCollectionsPolicyRequest request) {
        return CollectionsPolicyResponse.from(collectionsPolicyService.create(request));
    }

    @GetMapping("/collections-policy/current")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public CollectionsPolicyResponse current() {
        return CollectionsPolicyResponse.from(collectionsPolicyService.findCurrent());
    }
}

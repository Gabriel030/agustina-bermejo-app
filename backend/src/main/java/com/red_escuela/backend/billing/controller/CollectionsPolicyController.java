package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CollectionsPolicyResponse;
import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.billing.usecase.collectionspolicy.CreateCollectionsPolicyUseCase;
import com.red_escuela.backend.billing.usecase.collectionspolicy.FindCurrentCollectionsPolicyUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CollectionsPolicyController {
    private final CreateCollectionsPolicyUseCase createCollectionsPolicyUseCase;
    private final FindCurrentCollectionsPolicyUseCase findCurrentCollectionsPolicyUseCase;

    public CollectionsPolicyController(CreateCollectionsPolicyUseCase createCollectionsPolicyUseCase,
                                        FindCurrentCollectionsPolicyUseCase findCurrentCollectionsPolicyUseCase) {
        this.createCollectionsPolicyUseCase = createCollectionsPolicyUseCase;
        this.findCurrentCollectionsPolicyUseCase = findCurrentCollectionsPolicyUseCase;
    }

    @PostMapping("/collections-policy")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public CollectionsPolicyResponse create(@Valid @RequestBody CreateCollectionsPolicyRequest request) {
        return CollectionsPolicyResponse.from(createCollectionsPolicyUseCase.create(request));
    }

    @GetMapping("/collections-policy/current")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public CollectionsPolicyResponse current() {
        return CollectionsPolicyResponse.from(findCurrentCollectionsPolicyUseCase.findCurrent());
    }
}

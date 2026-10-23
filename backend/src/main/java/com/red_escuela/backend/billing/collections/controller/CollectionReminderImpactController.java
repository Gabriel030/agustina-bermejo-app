package com.red_escuela.backend.billing.collections.controller;

import com.red_escuela.backend.billing.collections.dto.CollectionReminderImpactRequest;
import com.red_escuela.backend.billing.collections.dto.CollectionReminderImpactResponse;
import com.red_escuela.backend.billing.collections.usecase.GetCollectionReminderImpactUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/collection-reminders")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class CollectionReminderImpactController {
    private final GetCollectionReminderImpactUseCase getImpactUseCase;

    public CollectionReminderImpactController(GetCollectionReminderImpactUseCase getImpactUseCase) {
        this.getImpactUseCase = getImpactUseCase;
    }

    @PostMapping("/impact")
    public CollectionReminderImpactResponse getImpact(@Valid @RequestBody CollectionReminderImpactRequest request) {
        return CollectionReminderImpactResponse.from(getImpactUseCase.getImpactOf(request.toQuery()));
    }
}

package com.red_escuela.backend.billing.collections.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.collections.dto.CollectionReminderImpactRequest;
import com.red_escuela.backend.billing.collections.dto.CollectionReminderImpactResponse;
import com.red_escuela.backend.billing.collections.usecase.GetCollectionReminderImpactUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/collection-reminders")
public class CollectionReminderImpactController {
    private final GetCollectionReminderImpactUseCase getImpactUseCase;

    public CollectionReminderImpactController(GetCollectionReminderImpactUseCase getImpactUseCase) {
        this.getImpactUseCase = getImpactUseCase;
    }

    @PostMapping("/impact")
    @RequiresPermission(code = "billing.preview_reminder_impact", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CollectionReminderImpactResponse getImpact(@Valid @RequestBody CollectionReminderImpactRequest request) {
        return CollectionReminderImpactResponse.from(getImpactUseCase.getImpactOf(request.toQuery()));
    }
}

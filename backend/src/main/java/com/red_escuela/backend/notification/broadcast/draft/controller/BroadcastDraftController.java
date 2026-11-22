package com.red_escuela.backend.notification.broadcast.draft.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.notification.broadcast.draft.dto.BroadcastDraftResponse;
import com.red_escuela.backend.notification.broadcast.draft.dto.CreateBroadcastDraftRequest;
import com.red_escuela.backend.notification.broadcast.draft.usecase.CreateBroadcastDraftUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/broadcasts")
public class BroadcastDraftController {

    private final CreateBroadcastDraftUseCase createBroadcastDraft;

    public BroadcastDraftController(CreateBroadcastDraftUseCase createBroadcastDraft) {
        this.createBroadcastDraft = createBroadcastDraft;
    }

    @RequiresPermission(code = "notification.compose_broadcast", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BroadcastDraftResponse createBroadcastDraft(@Valid @RequestBody CreateBroadcastDraftRequest request) {
        return createBroadcastDraft.createBroadcastDraft(request);
    }
}

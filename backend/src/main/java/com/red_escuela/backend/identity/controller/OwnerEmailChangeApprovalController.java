package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.EmailChangePreviewResponse;
import com.red_escuela.backend.identity.dto.EmailChangeTokenRequest;
import com.red_escuela.backend.identity.usecase.emailverification.ApproveEmailChangeUseCase;
import com.red_escuela.backend.identity.usecase.emailverification.PreviewEmailChangeUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OwnerEmailChangeApprovalController {

    private final PreviewEmailChangeUseCase previewEmailChangeUseCase;
    private final ApproveEmailChangeUseCase approveEmailChangeUseCase;

    public OwnerEmailChangeApprovalController(PreviewEmailChangeUseCase previewEmailChangeUseCase,
                                              ApproveEmailChangeUseCase approveEmailChangeUseCase) {
        this.previewEmailChangeUseCase = previewEmailChangeUseCase;
        this.approveEmailChangeUseCase = approveEmailChangeUseCase;
    }

    @PostMapping("/email-change-preview")
    public EmailChangePreviewResponse previewEmailChange(@Valid @RequestBody EmailChangeTokenRequest request) {
        return previewEmailChangeUseCase.previewForOwner(request.token());
    }

    @PostMapping("/approve-email-change")
    public void approveEmailChange(@Valid @RequestBody EmailChangeTokenRequest request) {
        approveEmailChangeUseCase.approveFromCurrentEmail(request.token());
    }
}

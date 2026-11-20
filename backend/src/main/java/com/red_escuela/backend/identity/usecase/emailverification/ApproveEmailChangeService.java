package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.service.EmailChangeReviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveEmailChangeService implements ApproveEmailChangeUseCase {

    private final EmailChangeReviewer emailChangeReviewer;

    public ApproveEmailChangeService(EmailChangeReviewer emailChangeReviewer) {
        this.emailChangeReviewer = emailChangeReviewer;
    }

    @Override
    public void approveFromCurrentEmail(String token) {
        emailChangeReviewer.approve(token);
    }
}

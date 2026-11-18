package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.service.EmailChangeCanceller;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelEmailChangeService implements CancelEmailChangeUseCase {

    private final EmailChangeCanceller emailChangeCanceller;

    public CancelEmailChangeService(EmailChangeCanceller emailChangeCanceller) {
        this.emailChangeCanceller = emailChangeCanceller;
    }

    @Override
    public void cancelFromPreviousEmail(String token) {
        emailChangeCanceller.cancelByClearToken(token);
    }
}

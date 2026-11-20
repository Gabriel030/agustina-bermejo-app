package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;

@UseCase
public class ApproveEmailChangeService implements ApproveEmailChangeUseCase {

    @Override
    public void approveFromCurrentEmail(String token) {
        throw new InvalidEmailVerificationTokenException();
    }
}

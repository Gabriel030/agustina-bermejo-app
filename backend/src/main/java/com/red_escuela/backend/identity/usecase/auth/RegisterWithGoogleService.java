package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.service.GoogleStudentRegistrationTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RegisterWithGoogleService implements RegisterWithGoogleUseCase {
    private final GoogleStudentRegistrationTransaction googleStudentRegistrationTransaction;

    public RegisterWithGoogleService(GoogleStudentRegistrationTransaction googleStudentRegistrationTransaction) {
        this.googleStudentRegistrationTransaction = googleStudentRegistrationTransaction;
    }

    @Override
    public AuthTokens register(RegisterWithGoogleCommand command) {
        return googleStudentRegistrationTransaction.register(command.idToken(), command.institutionId());
    }
}

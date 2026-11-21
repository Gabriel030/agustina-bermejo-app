package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.service.CredentialLoginTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class LoginService implements LoginUseCase {
    private final CredentialLoginTransaction credentialLoginTransaction;

    public LoginService(CredentialLoginTransaction credentialLoginTransaction) {
        this.credentialLoginTransaction = credentialLoginTransaction;
    }

    @Override
    public AuthTokens login(LoginCommand command) {
        return credentialLoginTransaction.login(command.username(), command.password(), command.ipAddress());
    }
}

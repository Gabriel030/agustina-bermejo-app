package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.service.StudentRegistrationTransaction;
import com.red_escuela.backend.identity.specialist.LegalRegistrationCompleter;
import com.red_escuela.backend.identity.specialist.PasswordPolicyValidator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RegisterService implements RegisterUseCase {
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final LegalRegistrationCompleter legalRegistrationCompleter;
    private final StudentRegistrationTransaction studentRegistrationTransaction;

    public RegisterService(PasswordPolicyValidator passwordPolicyValidator,
                           LegalRegistrationCompleter legalRegistrationCompleter,
                           StudentRegistrationTransaction studentRegistrationTransaction) {
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.legalRegistrationCompleter = legalRegistrationCompleter;
        this.studentRegistrationTransaction = studentRegistrationTransaction;
    }

    @Override
    public AuthTokens register(RegisterRequest request) {
        legalRegistrationCompleter.requireValid(request);
        passwordPolicyValidator.validate(request.password());
        return studentRegistrationTransaction.register(request);
    }
}

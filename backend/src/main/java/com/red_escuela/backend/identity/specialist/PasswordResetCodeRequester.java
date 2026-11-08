package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.api.PasswordResetCodeIssuer;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetCodeRequester {
    private final PasswordResetCodeIssuer passwordResetCodeIssuer;

    public PasswordResetCodeRequester(PasswordResetCodeIssuer passwordResetCodeIssuer) {
        this.passwordResetCodeIssuer = passwordResetCodeIssuer;
    }

    public void requestPasswordResetCode(String email) {
        passwordResetCodeIssuer.issuePasswordResetCode(email);
    }
}

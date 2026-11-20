package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.api.PasswordResetCodeIssuer;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetCodeRequester {
    private final PasswordResetCodeIssuer passwordResetCodeIssuer;
    private final PersonRepository personRepository;
    private final PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy;

    public PasswordResetCodeRequester(PasswordResetCodeIssuer passwordResetCodeIssuer, PersonRepository personRepository,
                                      PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy) {
        this.passwordResetCodeIssuer = passwordResetCodeIssuer;
        this.personRepository = personRepository;
        this.publicPasswordResetBlockPolicy = publicPasswordResetBlockPolicy;
    }

    public void requestPasswordResetCode(String email) {
        passwordResetCodeIssuer.issuePasswordResetCode(email);
    }

    public void requestPublicPasswordResetCode(String email) {
        boolean blocked = personRepository.findByEmail(EmailAddress.of(email))
                .filter(publicPasswordResetBlockPolicy::blocks)
                .isPresent();
        if (!blocked) {
            passwordResetCodeIssuer.issuePasswordResetCode(email);
        }
    }
}

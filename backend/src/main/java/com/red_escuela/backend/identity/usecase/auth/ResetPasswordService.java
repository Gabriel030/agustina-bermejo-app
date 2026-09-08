package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.PasswordPolicyValidator;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.util.TokenHashUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class ResetPasswordService implements ResetPasswordUseCase {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    public ResetPasswordService(PasswordResetTokenRepository passwordResetTokenRepository, PersonRepository personRepository,
                                 PasswordEncoder passwordEncoder, PasswordPolicyValidator passwordPolicyValidator) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
    }

    @Override
    @Transactional
    public void reset(ResetPasswordCommand command) {
        // Se valida la política ANTES de tocar el token: una contraseña inválida no
        // debería consumir el intercambio (RF-AUTH-05).
        passwordPolicyValidator.validate(command.newPassword());

        String hash = TokenHashUtil.sha256Hex(command.exchangeToken());
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByExchangeTokenHashAndExchangeExpiresAtAfter(hash, LocalDateTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Person person = resetToken.getPerson();
        person.setPassword(passwordEncoder.encode(command.newPassword()));
        personRepository.save(person);

        // Se limpia el hash (no alcanza con un booleano "used", ya lo estaba desde
        // verifyResetCode) para que este mismo intercambio no sirva una segunda vez.
        resetToken.setExchangeTokenHash(null);
        passwordResetTokenRepository.save(resetToken);
    }
}

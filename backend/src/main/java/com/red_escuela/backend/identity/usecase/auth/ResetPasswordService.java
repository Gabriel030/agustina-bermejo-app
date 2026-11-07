package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.PasswordPolicyValidator;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class ResetPasswordService implements ResetPasswordUseCase {
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final SessionRevoker sessionRevoker;

    public ResetPasswordService(PasswordResetTokenRepository passwordResetTokenRepository, PersonRepository personRepository,
                                 PasswordEncoder passwordEncoder, PasswordPolicyValidator passwordPolicyValidator,
                                 SessionRevoker sessionRevoker) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.sessionRevoker = sessionRevoker;
    }

    @Override
    @Transactional
    public void reset(ResetPasswordCommand command) {
        passwordPolicyValidator.validate(command.newPassword());

        String hash = TokenHashUtil.sha256Hex(command.exchangeToken());
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByExchangeTokenHashAndExchangeExpiresAtAfter(hash, ApplicationTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Person person = resetToken.getPerson();
        person.setPassword(passwordEncoder.encode(command.newPassword()));
        personRepository.save(person);
        sessionRevoker.revokeAllSessions(person);

        resetToken.setExchangeTokenHash(null);
        passwordResetTokenRepository.save(resetToken);
    }
}

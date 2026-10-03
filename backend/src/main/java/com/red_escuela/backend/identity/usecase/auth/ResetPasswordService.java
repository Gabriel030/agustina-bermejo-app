package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.PasswordPolicyValidator;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
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
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;

    public ResetPasswordService(PasswordResetTokenRepository passwordResetTokenRepository, PersonRepository personRepository,
                                 PasswordEncoder passwordEncoder, PasswordPolicyValidator passwordPolicyValidator,
                                 RevokeAllSessionsUseCase revokeAllSessionsUseCase) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
    }

    @Override
    @Transactional
    public void reset(ResetPasswordCommand command) {
        passwordPolicyValidator.validate(command.newPassword());

        String hash = TokenHashUtil.sha256Hex(command.exchangeToken());
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByExchangeTokenHashAndExchangeExpiresAtAfter(hash, LocalDateTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Person person = resetToken.getPerson();
        person.setPassword(passwordEncoder.encode(command.newPassword()));
        personRepository.save(person);
        revokeAllSessionsUseCase.revokeAll(person);

        resetToken.setExchangeTokenHash(null);
        passwordResetTokenRepository.save(resetToken);
    }
}

package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.SecureTokenGenerator;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.util.TokenHashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class VerifyResetCodeService implements VerifyResetCodeUseCase {
    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final int passwordResetCodeMaxAttempts;
    private final long passwordResetExchangeTokenExpirationMinutes;

    public VerifyResetCodeService(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                   SecureTokenGenerator secureTokenGenerator,
                                   @Value("${auth.password-reset-code-max-attempts}") int passwordResetCodeMaxAttempts,
                                   @Value("${auth.password-reset-exchange-token-expiration-minutes}") long passwordResetExchangeTokenExpirationMinutes) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.passwordResetCodeMaxAttempts = passwordResetCodeMaxAttempts;
        this.passwordResetExchangeTokenExpirationMinutes = passwordResetExchangeTokenExpirationMinutes;
    }

    @Override
    @Transactional(noRollbackFor = InvalidPasswordResetTokenException.class)
    public String verify(VerifyResetCodeCommand command) {
        Person person = personRepository.findByEmail(EmailAddress.of(command.email()))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findFirstByPersonAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(person, LocalDateTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (!resetToken.getTokenHash().equals(TokenHashUtil.sha256Hex(command.code()))) {
            registerFailedResetAttempt(resetToken);
            throw new InvalidPasswordResetTokenException();
        }

        String exchangeToken = secureTokenGenerator.generate();
        resetToken.setExchangeTokenHash(TokenHashUtil.sha256Hex(exchangeToken));
        resetToken.setExchangeExpiresAt(LocalDateTime.now().plusMinutes(passwordResetExchangeTokenExpirationMinutes));
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return exchangeToken;
    }

    private void registerFailedResetAttempt(PasswordResetToken resetToken) {
        int attempts = resetToken.getAttempts() + 1;
        resetToken.setAttempts(attempts);
        if (attempts >= passwordResetCodeMaxAttempts) {
            resetToken.setUsed(true);
        }
        passwordResetTokenRepository.save(resetToken);
    }
}

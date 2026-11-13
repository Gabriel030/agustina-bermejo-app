package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.PasswordResetCodeChecker;
import com.red_escuela.backend.identity.specialist.SecureTokenGenerator;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


@UseCase
public class VerifyResetCodeService implements VerifyResetCodeUseCase {
    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final int passwordResetCodeMaxAttempts;
    private final long passwordResetExchangeTokenExpirationMinutes;
    private final PasswordResetCodeChecker passwordResetCodeChecker;

    public VerifyResetCodeService(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                   SecureTokenGenerator secureTokenGenerator,
                                   @Value("${auth.password-reset-code-max-attempts}") int passwordResetCodeMaxAttempts,
                                   @Value("${auth.password-reset-exchange-token-expiration-minutes}") long passwordResetExchangeTokenExpirationMinutes,
                                   PasswordResetCodeChecker passwordResetCodeChecker) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.passwordResetCodeMaxAttempts = passwordResetCodeMaxAttempts;
        this.passwordResetExchangeTokenExpirationMinutes = passwordResetExchangeTokenExpirationMinutes;
        this.passwordResetCodeChecker = passwordResetCodeChecker;
    }

    @Override
    @Transactional(noRollbackFor = InvalidPasswordResetTokenException.class)
    public String verify(VerifyResetCodeCommand command) {
        Person person = personRepository.findByEmail(EmailAddress.of(command.email()))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        passwordResetCodeChecker.requireNotLocked(person, command.ipAddress());

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findFirstByPersonAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(person, ApplicationTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        byte[] expectedHash = resetToken.getTokenHash().getBytes(StandardCharsets.UTF_8);
        byte[] providedHash = TokenHashUtil.sha256Hex(command.code()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedHash, providedHash)) {
            registerFailedResetAttempt(resetToken);
            passwordResetCodeChecker.registerFailedAttempt(person, resetToken, command.ipAddress());
            throw new InvalidPasswordResetTokenException();
        }
        passwordResetCodeChecker.clearFailedAttempts(person, command.ipAddress());

        String exchangeToken = secureTokenGenerator.generate();
        resetToken.setExchangeTokenHash(TokenHashUtil.sha256Hex(exchangeToken));
        resetToken.setExchangeExpiresAt(ApplicationTime.now().plusMinutes(passwordResetExchangeTokenExpirationMinutes));
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

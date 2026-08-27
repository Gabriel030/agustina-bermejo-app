package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.AuthProvider;
import com.red_escuela.backend.entity.LoginAttempt;
import com.red_escuela.backend.entity.PasswordResetToken;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.RefreshToken;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.InvalidCredentialsException;
import com.red_escuela.backend.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.repository.LoginAttemptRepository;
import com.red_escuela.backend.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RefreshTokenRepository;
import com.red_escuela.backend.util.TokenHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Orquesta login/refresh/logout. A diferencia de JwtService, sí toca la base de datos.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final PersonRepository personRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int lockoutMaxAttempts;
    private final long lockoutDurationMinutes;
    private final long refreshTokenExpirationDays;
    private final long passwordResetTokenExpirationMinutes;
    private final int passwordMinLength;
    private final boolean passwordRequireLetterAndDigit;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            PersonRepository personRepository,
            RefreshTokenRepository refreshTokenRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${auth.lockout.max-attempts}") int lockoutMaxAttempts,
            @Value("${auth.lockout.duration-minutes}") long lockoutDurationMinutes,
            @Value("${jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays,
            @Value("${jwt.password-reset-token-expiration-minutes}") long passwordResetTokenExpirationMinutes,
            @Value("${auth.password.min-length}") int passwordMinLength,
            @Value("${auth.password.require-letter-and-digit}") boolean passwordRequireLetterAndDigit) {
        this.personRepository = personRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
        this.passwordResetTokenExpirationMinutes = passwordResetTokenExpirationMinutes;
        this.passwordMinLength = passwordMinLength;
        this.passwordRequireLetterAndDigit = passwordRequireLetterAndDigit;
    }

    @Transactional
    public AuthTokens login(String username, String rawPassword, String ipAddress) {
        Optional<Person> personOpt = personRepository.findByUsername(username);

        if (personOpt.isEmpty()) {
            recordAttempt(username, null, false, ipAddress);
            throw new InvalidCredentialsException();
        }

        Person person = personOpt.get();

        // Cuenta bloqueada, sin password, de otro auth provider (Google) o dada de baja
        // (Spec Gestión de personas, RF-PR-03: status != ACTIVE tras un withdraw) se
        // rechaza sin intentar el match, mismo error genérico en todos los casos.
        if (isLocked(person) || person.getPassword() == null || person.getAuthProvider() != AuthProvider.LOCAL
                || person.getStatus() != StatusType.ACTIVE) {
            recordAttempt(username, person, false, ipAddress);
            throw new InvalidCredentialsException();
        }

        boolean matches = passwordEncoder.matches(rawPassword, person.getPassword());
        recordAttempt(username, person, matches, ipAddress);

        if (!matches) {
            registerFailedAttempt(person);
            throw new InvalidCredentialsException();
        }

        resetLockout(person);
        return issueTokens(person);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        String hash = TokenHashUtil.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .filter(rt -> !rt.getRevoked())
                .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidRefreshTokenException::new);

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokens(existing.getPerson());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = TokenHashUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<Person> personOpt = personRepository.findByEmail(email);
        if (personOpt.isEmpty()) {
            // Anti-enumeración: no revela si el email existe, simplemente no genera nada.
            return;
        }
        Person person = personOpt.get();

        String rawToken = generateRandomToken();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setPerson(person);
        resetToken.setTokenHash(TokenHashUtil.sha256Hex(rawToken));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenExpirationMinutes));
        resetToken.setUsed(false);
        resetToken.setCreatedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        // Placeholder hasta que exista envío real de email (RF-AUTH-03, fuera de esta spec).
        log.info("Token de recuperación de password generado para person {}: {}", person.getId(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        // Se valida la política ANTES de tocar el token: una contraseña inválida no debería
        // consumir un token de reset de un solo uso (RF-AUTH-05).
        validarPoliticaDePassword(newPassword);

        String hash = TokenHashUtil.sha256Hex(rawToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .filter(t -> !t.getUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Person person = resetToken.getPerson();
        person.setPassword(passwordEncoder.encode(newPassword));
        personRepository.save(person);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    private AuthTokens issueTokens(Person person) {
        String accessToken = jwtService.generateAccessToken(person);
        String refreshToken = issueRefreshToken(person);
        long expiresIn = Duration.ofMinutes(jwtService.getAccessTokenExpirationMinutes()).toSeconds();
        return new AuthTokens(accessToken, refreshToken, expiresIn);
    }

    private String issueRefreshToken(Person person) {
        String rawToken = generateRandomToken();

        RefreshToken entity = new RefreshToken();
        entity.setPerson(person);
        entity.setTokenHash(TokenHashUtil.sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays));
        entity.setRevoked(false);
        entity.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    // RF-AUTH-05: política mínima configurable (auth.password.*), no hardcodeada.
    private void validarPoliticaDePassword(String rawPassword) {
        if (rawPassword.length() < passwordMinLength) {
            throw new InvalidPasswordPolicyException(
                    "La contraseña tiene que tener al menos " + passwordMinLength + " caracteres");
        }
        if (passwordRequireLetterAndDigit) {
            boolean tieneLetra = rawPassword.chars().anyMatch(Character::isLetter);
            boolean tieneDigito = rawPassword.chars().anyMatch(Character::isDigit);
            if (!tieneLetra || !tieneDigito) {
                throw new InvalidPasswordPolicyException(
                        "La contraseña tiene que tener al menos una letra y un número");
            }
        }
    }

    private String generateRandomToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private boolean isLocked(Person person) {
        return person.getLockedUntil() != null && person.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void registerFailedAttempt(Person person) {
        int attempts = person.getFailedLoginAttempts() + 1;
        if (attempts >= lockoutMaxAttempts) {
            person.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
            person.setFailedLoginAttempts(0);
        } else {
            person.setFailedLoginAttempts(attempts);
        }
        personRepository.save(person);
    }

    private void resetLockout(Person person) {
        person.setFailedLoginAttempts(0);
        person.setLockedUntil(null);
        personRepository.save(person);
    }

    private void recordAttempt(String attemptedUsername, Person person, boolean successful, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setAttemptedUsername(attemptedUsername);
        attempt.setPerson(person);
        attempt.setSuccessful(successful);
        attempt.setIpAddress(ipAddress);
        attempt.setAttemptedAt(LocalDateTime.now());
        loginAttemptRepository.save(attempt);
    }
}

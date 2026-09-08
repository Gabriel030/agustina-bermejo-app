package com.red_escuela.backend.identity.service;
import com.red_escuela.backend.service.EmailService;

import com.red_escuela.backend.identity.entity.LoginAttempt;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.exception.GoogleAccountNotLinkedException;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.LoginAttemptRepository;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
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
    private final GoogleIdTokenService googleIdTokenService;
    private final EmailService emailService;
    private final int lockoutMaxAttempts;
    private final long lockoutDurationMinutes;
    private final long refreshTokenExpirationDays;
    private final long passwordResetCodeExpirationMinutes;
    private final int passwordResetCodeMaxAttempts;
    private final long passwordResetExchangeTokenExpirationMinutes;
    private final int passwordMinLength;
    private final boolean passwordRequireLetterAndDigit;
    private final boolean passwordRequireUppercase;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            PersonRepository personRepository,
            RefreshTokenRepository refreshTokenRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            GoogleIdTokenService googleIdTokenService,
            EmailService emailService,
            @Value("${auth.lockout.max-attempts}") int lockoutMaxAttempts,
            @Value("${auth.lockout.duration-minutes}") long lockoutDurationMinutes,
            @Value("${jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays,
            @Value("${auth.password-reset-code-expiration-minutes}") long passwordResetCodeExpirationMinutes,
            @Value("${auth.password-reset-code-max-attempts}") int passwordResetCodeMaxAttempts,
            @Value("${auth.password-reset-exchange-token-expiration-minutes}") long passwordResetExchangeTokenExpirationMinutes,
            @Value("${auth.password.min-length}") int passwordMinLength,
            @Value("${auth.password.require-letter-and-digit}") boolean passwordRequireLetterAndDigit,
            @Value("${auth.password.require-uppercase}") boolean passwordRequireUppercase) {
        this.personRepository = personRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleIdTokenService = googleIdTokenService;
        this.emailService = emailService;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
        this.passwordResetCodeExpirationMinutes = passwordResetCodeExpirationMinutes;
        this.passwordResetCodeMaxAttempts = passwordResetCodeMaxAttempts;
        this.passwordResetExchangeTokenExpirationMinutes = passwordResetExchangeTokenExpirationMinutes;
        this.passwordMinLength = passwordMinLength;
        this.passwordRequireLetterAndDigit = passwordRequireLetterAndDigit;
        this.passwordRequireUppercase = passwordRequireUppercase;
    }

    @Transactional
    public AuthTokens login(String username, String rawPassword, String ipAddress) {
        Optional<Person> personOpt = personRepository.findByUsername(username);

        if (personOpt.isEmpty()) {
            recordAttempt(username, null, false, ipAddress);
            throw new InvalidCredentialsException();
        }

        Person person = personOpt.get();

        // Cuenta bloqueada, sin password seteado (alta por Google que nunca pasó por
        // "olvidé mi contraseña" para ponerse una) o dada de baja (Spec Gestión de
        // personas, RF-PR-03: status != ACTIVE tras un withdraw) se rechaza sin intentar
        // el match, mismo error genérico en todos los casos. No se filtra más por
        // authProvider: una Person GOOGLE que consiguió password vía reset entra igual.
        if (isLocked(person) || person.getPassword() == null
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

    // Épica 1 Fase 2: no reusa login() — el contrato de entrada es distinto (idToken de Google,
    // no username/password) y mezclar ambos casos degradaría la legibilidad de login(), que ya
    // tiene lógica de lockout/rate-limit específica del flujo local que acá no aplica.
    @Transactional
    public AuthTokens loginWithGoogle(String idToken) {
        GoogleIdTokenService.GoogleProfile profile = googleIdTokenService.verify(idToken);

        Optional<Person> byGoogleId = personRepository.findByGoogleSubjectId(profile.subject());
        if (byGoogleId.isPresent()) {
            return issueTokens(byGoogleId.get());
        }

        Person person = personRepository.findByEmail(profile.email())
                .orElseThrow(() -> new GoogleAccountNotLinkedException(profile.email()));

        // Auto-vinculación por email verificado (decisión de producto — la spec original
        // recomendaba no vincular, pero se pidió explícitamente permitirlo): no toca
        // authProvider, así que login() local sigue funcionando igual para esta Person.
        person.setGoogleSubjectId(profile.subject());
        personRepository.save(person);
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
        // Universal: cualquier Person con este email recibe el código, sea LOCAL, GOOGLE,
        // o ya tenga ambos métodos — no hay ningún caso en el que este flujo no haga nada
        // salvo que el email directamente no exista (anti-enumeración).
        Optional<Person> personOpt = personRepository.findByEmail(email);
        if (personOpt.isEmpty()) {
            return;
        }
        Person person = personOpt.get();

        String code = generateResetCode();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setPerson(person);
        resetToken.setTokenHash(TokenHashUtil.sha256Hex(code));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetCodeExpirationMinutes));
        resetToken.setUsed(false);
        resetToken.setAttempts(0);
        resetToken.setCreatedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetCode(person, code);
        log.info("Código de recuperación de password generado para person {}", person.getId());
    }

    // noRollbackFor: sin esto, el incremento de attempts (registerFailedResetAttempt)
    // se pierde — al lanzar InvalidPasswordResetTokenException dentro de la misma
    // transacción, Spring hace rollback de TODO lo hecho en el método, incluido el
    // save() del intento fallido. El intento fallido tiene que sobrevivir aunque la
    // operación en sí termine en excepción (verificado a mano: sin este flag, un
    // código incorrecto nunca incrementaba attempts en la base real, solo parecía
    // funcionar en tests porque corrían dentro de la misma transacción sin commit real).
    //
    // Devuelve un token de intercambio de un solo uso, corta duración — separa "probaste
    // que el código te llegó" de "ya elegiste la contraseña nueva", así la pantalla de
    // contraseña no necesita volver a pedir el código (Épica 1 Fase 4, UX de dos pasos).
    @Transactional(noRollbackFor = InvalidPasswordResetTokenException.class)
    public String verifyResetCode(String email, String code) {
        Person person = personRepository.findByEmail(email)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findFirstByPersonAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(person, LocalDateTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (!resetToken.getTokenHash().equals(TokenHashUtil.sha256Hex(code))) {
            registerFailedResetAttempt(resetToken);
            throw new InvalidPasswordResetTokenException();
        }

        String exchangeToken = generateRandomToken();
        resetToken.setExchangeTokenHash(TokenHashUtil.sha256Hex(exchangeToken));
        resetToken.setExchangeExpiresAt(LocalDateTime.now().plusMinutes(passwordResetExchangeTokenExpirationMinutes));
        // El código de 6 dígitos ya cumplió su función — se marca usado para que no sirva
        // para un segundo intercambio, aunque la contraseña todavía no se haya cambiado.
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return exchangeToken;
    }

    @Transactional
    public void resetPassword(String exchangeToken, String newPassword) {
        // Se valida la política ANTES de tocar el token: una contraseña inválida no
        // debería consumir el intercambio (RF-AUTH-05).
        validarPoliticaDePassword(newPassword);

        String hash = TokenHashUtil.sha256Hex(exchangeToken);
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByExchangeTokenHashAndExchangeExpiresAtAfter(hash, LocalDateTime.now())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Person person = resetToken.getPerson();
        person.setPassword(passwordEncoder.encode(newPassword));
        personRepository.save(person);

        // Se limpia el hash (no alcanza con un booleano "used", ya lo estaba desde
        // verifyResetCode) para que este mismo intercambio no sirva una segunda vez.
        resetToken.setExchangeTokenHash(null);
        passwordResetTokenRepository.save(resetToken);
    }

    private void registerFailedResetAttempt(PasswordResetToken resetToken) {
        int attempts = resetToken.getAttempts() + 1;
        resetToken.setAttempts(attempts);
        if (attempts >= passwordResetCodeMaxAttempts) {
            resetToken.setUsed(true);
        }
        passwordResetTokenRepository.save(resetToken);
    }

    private String generateResetCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    // Package-private (no private): RegistrationService reusa exactamente este
    // mismo mecanismo para loguear a alguien recién registrado, sin duplicarlo.
    AuthTokens issueTokens(Person person) {
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
    // Package-private: RegistrationService la reusa para el registro independiente.
    void validarPoliticaDePassword(String rawPassword) {
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
        if (passwordRequireUppercase && rawPassword.chars().noneMatch(Character::isUpperCase)) {
            throw new InvalidPasswordPolicyException("La contraseña tiene que tener al menos una mayúscula");
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

package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.AuthProvider;
import com.red_escuela.backend.entity.LoginAttempt;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.RefreshToken;
import com.red_escuela.backend.exception.InvalidCredentialsException;
import com.red_escuela.backend.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.repository.LoginAttemptRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RefreshTokenRepository;
import com.red_escuela.backend.util.TokenHashUtil;
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

    private final PersonRepository personRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int lockoutMaxAttempts;
    private final long lockoutDurationMinutes;
    private final long refreshTokenExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            PersonRepository personRepository,
            RefreshTokenRepository refreshTokenRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${auth.lockout.max-attempts}") int lockoutMaxAttempts,
            @Value("${auth.lockout.duration-minutes}") long lockoutDurationMinutes,
            @Value("${jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays) {
        this.personRepository = personRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    @Transactional
    public AuthTokens login(String username, String rawPassword, String ipAddress) {
        Optional<Person> personOpt = personRepository.findByUsername(username);

        if (personOpt.isEmpty()) {
            recordAttempt(username, null, false, ipAddress);
            throw new InvalidCredentialsException();
        }

        Person person = personOpt.get();

        // Cuenta bloqueada, sin password (sin grant_system_access) o de otro auth
        // provider (Google): se rechaza sin intentar el match, mismo error genérico.
        if (isLocked(person) || person.getPassword() == null || person.getAuthProvider() != AuthProvider.LOCAL) {
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

    private AuthTokens issueTokens(Person person) {
        String accessToken = jwtService.generateAccessToken(person);
        String refreshToken = issueRefreshToken(person);
        long expiresIn = Duration.ofMinutes(jwtService.getAccessTokenExpirationMinutes()).toSeconds();
        return new AuthTokens(accessToken, refreshToken, expiresIn);
    }

    private String issueRefreshToken(Person person) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken entity = new RefreshToken();
        entity.setPerson(person);
        entity.setTokenHash(TokenHashUtil.sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays));
        entity.setRevoked(false);
        entity.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(entity);

        return rawToken;
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

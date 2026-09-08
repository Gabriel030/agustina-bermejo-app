package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.AuthResponse;
import com.red_escuela.backend.identity.dto.ForgotPasswordRequest;
import com.red_escuela.backend.identity.dto.GoogleLoginRequest;
import com.red_escuela.backend.identity.dto.GoogleRegisterRequest;
import com.red_escuela.backend.identity.dto.LoginRequest;
import com.red_escuela.backend.dto.LogoutRequest;
import com.red_escuela.backend.identity.dto.RefreshRequest;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.dto.ResetPasswordRequest;
import com.red_escuela.backend.dto.VerifyResetCodeRequest;
import com.red_escuela.backend.dto.VerifyResetCodeResponse;
import com.red_escuela.backend.identity.exception.TooManyRequestsException;
import com.red_escuela.backend.identity.service.AuthService;
import com.red_escuela.backend.identity.service.AuthTokens;
import com.red_escuela.backend.identity.service.LoginRateLimiter;
import com.red_escuela.backend.identity.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, RegistrationService registrationService, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeRegister(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Demasiados registros desde esta IP, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = registrationService.register(request);
        return AuthResponse.from(tokens);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!rateLimiter.tryConsumeLogin(ip)) {
            throw new TooManyRequestsException("Demasiados intentos de login, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = authService.login(request.username(), request.password(), ip);
        return AuthResponse.from(tokens);
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeLogin(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Demasiados intentos de login, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = authService.loginWithGoogle(request.idToken());
        return AuthResponse.from(tokens);
    }

    @PostMapping("/google/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerWithGoogle(@Valid @RequestBody GoogleRegisterRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeRegister(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Demasiados registros desde esta IP, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = registrationService.registerWithGoogle(request.idToken(), request.institutionId());
        return AuthResponse.from(tokens);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        AuthTokens tokens = authService.refresh(request.refreshToken());
        return AuthResponse.from(tokens);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeForgotPassword(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Demasiadas solicitudes de recuperación, esperá un momento antes de reintentar");
        }
        // Siempre 202, exista o no el email: no filtrar qué emails están registrados.
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/verify-reset-code")
    public VerifyResetCodeResponse verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeForgotPassword(httpRequest.getRemoteAddr())) {
            throw new TooManyRequestsException("Demasiados intentos, esperá un momento antes de reintentar");
        }
        String resetToken = authService.verifyResetCode(request.email(), request.code());
        return VerifyResetCodeResponse.from(resetToken);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.resetToken(), request.newPassword());
    }
}

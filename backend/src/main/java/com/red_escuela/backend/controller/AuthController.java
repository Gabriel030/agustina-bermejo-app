package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AuthResponse;
import com.red_escuela.backend.dto.ForgotPasswordRequest;
import com.red_escuela.backend.dto.LoginRequest;
import com.red_escuela.backend.dto.LogoutRequest;
import com.red_escuela.backend.dto.RefreshRequest;
import com.red_escuela.backend.dto.RegisterRequest;
import com.red_escuela.backend.dto.ResetPasswordRequest;
import com.red_escuela.backend.exception.TooManyRequestsException;
import com.red_escuela.backend.service.AuthService;
import com.red_escuela.backend.service.AuthTokens;
import com.red_escuela.backend.service.LoginRateLimiter;
import com.red_escuela.backend.service.RegistrationService;
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
        AuthTokens tokens = registrationService.registrar(request);
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

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
    }
}

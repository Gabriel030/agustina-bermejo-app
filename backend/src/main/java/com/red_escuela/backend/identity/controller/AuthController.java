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
import com.red_escuela.backend.identity.exception.RateLimitExceededException;
import com.red_escuela.backend.identity.infrastructure.LoginRateLimiter;
import com.red_escuela.backend.identity.usecase.auth.AuthTokens;
import com.red_escuela.backend.identity.usecase.auth.LoginCommand;
import com.red_escuela.backend.identity.usecase.auth.LoginUseCase;
import com.red_escuela.backend.identity.usecase.auth.LoginWithGoogleUseCase;
import com.red_escuela.backend.identity.usecase.auth.LogoutUseCase;
import com.red_escuela.backend.identity.usecase.auth.RefreshTokenUseCase;
import com.red_escuela.backend.identity.usecase.auth.RegisterUseCase;
import com.red_escuela.backend.identity.usecase.auth.RegisterWithGoogleCommand;
import com.red_escuela.backend.identity.usecase.auth.RegisterWithGoogleUseCase;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.identity.usecase.auth.ResetPasswordCommand;
import com.red_escuela.backend.identity.usecase.auth.ResetPasswordUseCase;
import com.red_escuela.backend.identity.usecase.auth.VerifyResetCodeCommand;
import com.red_escuela.backend.identity.usecase.auth.VerifyResetCodeUseCase;
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
    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final LoginWithGoogleUseCase loginWithGoogleUseCase;
    private final RegisterWithGoogleUseCase registerWithGoogleUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final VerifyResetCodeUseCase verifyResetCodeUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final LoginRateLimiter rateLimiter;

    public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase,
                           LoginWithGoogleUseCase loginWithGoogleUseCase, RegisterWithGoogleUseCase registerWithGoogleUseCase,
                           RefreshTokenUseCase refreshTokenUseCase, LogoutUseCase logoutUseCase,
                           RequestPasswordResetUseCase requestPasswordResetUseCase, VerifyResetCodeUseCase verifyResetCodeUseCase,
                           ResetPasswordUseCase resetPasswordUseCase, LoginRateLimiter rateLimiter) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.loginWithGoogleUseCase = loginWithGoogleUseCase;
        this.registerWithGoogleUseCase = registerWithGoogleUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.verifyResetCodeUseCase = verifyResetCodeUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeRegister(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Demasiados registros desde esta IP, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = registerUseCase.register(request);
        return AuthResponse.from(tokens);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!rateLimiter.tryConsumeLogin(ip)) {
            throw new RateLimitExceededException("Demasiados intentos de login, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = loginUseCase.login(new LoginCommand(request.username(), request.password(), ip));
        return AuthResponse.from(tokens);
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeLogin(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Demasiados intentos de login, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = loginWithGoogleUseCase.login(request.idToken());
        return AuthResponse.from(tokens);
    }

    @PostMapping("/google/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerWithGoogle(@Valid @RequestBody GoogleRegisterRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeRegister(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Demasiados registros desde esta IP, esperá un momento antes de reintentar");
        }
        AuthTokens tokens = registerWithGoogleUseCase.register(new RegisterWithGoogleCommand(request.idToken(), request.institutionId()));
        return AuthResponse.from(tokens);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        AuthTokens tokens = refreshTokenUseCase.refresh(request.refreshToken());
        return AuthResponse.from(tokens);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        logoutUseCase.logout(request.refreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeForgotPassword(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Demasiadas solicitudes de recuperación, esperá un momento antes de reintentar");
        }
        requestPasswordResetUseCase.requestReset(request.email());
    }

    @PostMapping("/verify-reset-code")
    public VerifyResetCodeResponse verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsumeForgotPassword(httpRequest.getRemoteAddr())) {
            throw new RateLimitExceededException("Demasiados intentos, esperá un momento antes de reintentar");
        }
        String resetToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(request.email(), request.code()));
        return VerifyResetCodeResponse.from(resetToken);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.reset(new ResetPasswordCommand(request.resetToken(), request.newPassword()));
    }
}

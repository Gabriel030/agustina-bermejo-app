package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyValidator {

    private final int passwordMinLength;
    private final boolean passwordRequireLetterAndDigit;
    private final boolean passwordRequireUppercase;

    public PasswordPolicyValidator(
            @Value("${auth.password.min-length}") int passwordMinLength,
            @Value("${auth.password.require-letter-and-digit}") boolean passwordRequireLetterAndDigit,
            @Value("${auth.password.require-uppercase}") boolean passwordRequireUppercase) {
        this.passwordMinLength = passwordMinLength;
        this.passwordRequireLetterAndDigit = passwordRequireLetterAndDigit;
        this.passwordRequireUppercase = passwordRequireUppercase;
    }

    public void validate(String rawPassword) {
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
}

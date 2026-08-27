package com.red_escuela.backend.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("Ya existe una cuenta con el usuario " + username);
    }
}

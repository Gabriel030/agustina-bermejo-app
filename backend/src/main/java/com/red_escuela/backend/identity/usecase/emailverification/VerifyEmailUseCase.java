package com.red_escuela.backend.identity.usecase.emailverification;

public interface VerifyEmailUseCase {

    void verify(String token);
}

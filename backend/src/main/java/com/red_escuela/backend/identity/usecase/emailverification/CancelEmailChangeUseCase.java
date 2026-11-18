package com.red_escuela.backend.identity.usecase.emailverification;

public interface CancelEmailChangeUseCase {

    void cancelFromPreviousEmail(String token);
}

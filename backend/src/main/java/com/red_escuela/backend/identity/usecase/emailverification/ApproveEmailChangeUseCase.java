package com.red_escuela.backend.identity.usecase.emailverification;

public interface ApproveEmailChangeUseCase {

    void approveFromCurrentEmail(String token);
}

package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.ChangePasswordRequest;

public interface ChangeOwnPasswordUseCase {
    void changePassword(ChangePasswordRequest request);
}

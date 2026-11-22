package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.service.OwnPasswordChangeTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangeOwnPasswordService implements ChangeOwnPasswordUseCase {

    private final OwnPasswordChangeTransaction ownPasswordChangeTransaction;

    public ChangeOwnPasswordService(OwnPasswordChangeTransaction ownPasswordChangeTransaction) {
        this.ownPasswordChangeTransaction = ownPasswordChangeTransaction;
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        ownPasswordChangeTransaction.change(request.currentPassword(), request.newPassword());
    }
}

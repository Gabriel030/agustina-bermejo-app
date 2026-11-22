package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.OwnProfileUpdateTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateOwnProfileService implements UpdateOwnProfileUseCase {

    private final OwnProfileUpdateTransaction ownProfileUpdateTransaction;

    public UpdateOwnProfileService(OwnProfileUpdateTransaction ownProfileUpdateTransaction) {
        this.ownProfileUpdateTransaction = ownProfileUpdateTransaction;
    }

    @Override
    public Person updateOwnProfile(UpdateOwnProfileRequest request) {
        return ownProfileUpdateTransaction.update(request);
    }
}

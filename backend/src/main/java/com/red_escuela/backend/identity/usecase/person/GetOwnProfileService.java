package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetOwnProfileService implements GetOwnProfileUseCase {
    private final CurrentPersonService currentPersonService;

    public GetOwnProfileService(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    @Override
    public Person getOwnProfile() {
        return currentPersonService.resolveCurrentPerson();
    }
}

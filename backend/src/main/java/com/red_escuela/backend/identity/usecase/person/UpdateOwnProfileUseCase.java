package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;

public interface UpdateOwnProfileUseCase {
    Person updateOwnProfile(UpdateOwnProfileRequest request);
}

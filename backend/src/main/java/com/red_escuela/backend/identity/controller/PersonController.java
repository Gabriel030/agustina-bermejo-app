package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.PersonResponse;
import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.usecase.person.GetOwnProfileUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateOwnProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class PersonController {

    private final GetOwnProfileUseCase getOwnProfileUseCase;
    private final UpdateOwnProfileUseCase updateOwnProfileUseCase;

    public PersonController(GetOwnProfileUseCase getOwnProfileUseCase, UpdateOwnProfileUseCase updateOwnProfileUseCase) {
        this.getOwnProfileUseCase = getOwnProfileUseCase;
        this.updateOwnProfileUseCase = updateOwnProfileUseCase;
    }

    @GetMapping
    public PersonResponse getOwnProfile() {
        return PersonResponse.from(getOwnProfileUseCase.getOwnProfile());
    }

    @PutMapping
    public PersonResponse updateOwnProfile(@Valid @RequestBody UpdateOwnProfileRequest request) {
        return PersonResponse.from(updateOwnProfileUseCase.updateOwnProfile(request));
    }
}

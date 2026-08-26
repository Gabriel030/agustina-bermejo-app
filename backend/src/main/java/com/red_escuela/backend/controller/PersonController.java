package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.PersonResponse;
import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.service.PersonProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class PersonController {

    private final PersonProfileService personProfileService;

    public PersonController(PersonProfileService personProfileService) {
        this.personProfileService = personProfileService;
    }

    @PutMapping
    public PersonResponse actualizarDatosPropios(@Valid @RequestBody UpdateOwnProfileRequest request) {
        return PersonResponse.from(personProfileService.actualizarDatosPropios(request));
    }
}

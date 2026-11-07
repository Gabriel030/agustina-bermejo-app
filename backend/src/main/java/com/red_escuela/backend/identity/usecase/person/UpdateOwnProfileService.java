package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.identity.specialist.OwnDocumentChanger;
import com.red_escuela.backend.identity.specialist.EmailChangeRequester;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateOwnProfileService implements UpdateOwnProfileUseCase {

    private final PersonRepository personRepository;
    private final CurrentPersonService currentPersonService;

    private final EmailChangeRequester emailChangeRequester;
    private final OwnDocumentChanger ownDocumentChanger;

    public UpdateOwnProfileService(PersonRepository personRepository, CurrentPersonService currentPersonService,
                                   EmailChangeRequester emailChangeRequester,
                                   OwnDocumentChanger ownDocumentChanger) {
        this.personRepository = personRepository;
        this.currentPersonService = currentPersonService;
        this.emailChangeRequester = emailChangeRequester;
        this.ownDocumentChanger = ownDocumentChanger;
    }

    @Override
    @Transactional
    public Person updateOwnProfile(UpdateOwnProfileRequest request) {
        Person person = currentPersonService.resolveCurrentPerson();

        if (request.email() != null) {
            emailChangeRequester.requestOwnChange(person, request.email(), request.currentPassword());
        }
        if (request.telefono() != null) {
            person.setPhoneNumber(request.telefono());
        }
        if (request.sexo() != null) {
            person.setSex(request.sexo());
        }
        if (request.dni() != null && !request.dni().isBlank()) {
            ownDocumentChanger.changeDocument(person, request.documentType(), request.dni(), request.currentPassword());
        }

        var address = person.getAddress();
        if (request.street() != null) {
            address.setStreet(request.street());
        }
        if (request.number() != null) {
            address.setNumber(request.number());
        }
        if (request.floor() != null) {
            address.setFloor(request.floor());
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.state() != null) {
            address.setState(request.state());
        }
        if (request.postalCode() != null) {
            address.setPostalCode(request.postalCode());
        }
        if (request.country() != null) {
            address.setCountry(request.country());
        }

        return personRepository.saveAndFlush(person);
    }
}

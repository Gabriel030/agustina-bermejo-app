package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.shared.domain.DocumentNumber;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.identity.exception.EmailAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateOwnProfileService implements UpdateOwnProfileUseCase {

    private final PersonRepository personRepository;
    private final CurrentPersonService currentPersonService;

    public UpdateOwnProfileService(PersonRepository personRepository, CurrentPersonService currentPersonService) {
        this.personRepository = personRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public Person updateOwnProfile(UpdateOwnProfileRequest request) {
        Person person = currentPersonService.resolveCurrentPerson();

        if (request.email() != null) {
            EmailAddress newEmail = EmailAddress.of(request.email());
            personRepository.findByEmail(newEmail)
                    .filter(owner -> !owner.getId().equals(person.getId()))
                    .ifPresent(owner -> {
                        throw new EmailAlreadyExistsException(request.email());
                    });
            person.setEmail(newEmail);
        }
        if (request.telefono() != null) {
            person.setPhoneNumber(request.telefono());
        }
        if (request.sexo() != null) {
            person.setSex(request.sexo());
        }
        if (request.dni() != null && !request.dni().isBlank()) {
            DocumentNumber document = DocumentNumber.of(
                    request.documentType() == null ? DocumentType.DNI : request.documentType(), request.dni());
            personRepository.findByDocumentTypeAndDni(document.type(), document.value())
                    .filter(owner -> !owner.getId().equals(person.getId()))
                    .ifPresent(owner -> {
                        throw new DniAlreadyExistsException(document.type(), document.value());
                    });
            person.changeDocument(document.type(), document.value());
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

package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.DocumentNumber;
import com.red_escuela.backend.shared.domain.DocumentType;
import org.springframework.stereotype.Service;

@Service
public class OwnDocumentChanger {

    private final PersonRepository personRepository;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final ContactChangeRecorder contactChangeRecorder;

    public OwnDocumentChanger(PersonRepository personRepository, CurrentPasswordVerifier currentPasswordVerifier,
                              ContactChangeRecorder contactChangeRecorder) {
        this.personRepository = personRepository;
        this.currentPasswordVerifier = currentPasswordVerifier;
        this.contactChangeRecorder = contactChangeRecorder;
    }

    public void changeDocument(Person person, DocumentType requestedType, String requestedNumber, String currentPassword) {
        DocumentNumber document = DocumentNumber.of(requestedType == null ? DocumentType.DNI : requestedType, requestedNumber);
        boolean unchanged = document.type() == person.getDocumentType() && document.value().equals(person.getDni());
        if (unchanged) {
            return;
        }
        boolean documentAlreadyLoaded = person.getDni() != null && !person.getDni().isBlank();
        if (documentAlreadyLoaded) {
            currentPasswordVerifier.requireMatches(person, currentPassword);
        }
        personRepository.findByDocumentTypeAndDni(document.type(), document.value())
                .filter(owner -> !owner.getId().equals(person.getId()))
                .ifPresent(owner -> {
                    throw new DniAlreadyExistsException(document.type(), document.value());
                });
        String previousDocument = person.getDocumentType() + ":" + person.getDni();
        person.changeDocument(document.type(), document.value());
        contactChangeRecorder.recordChange(person.getId(), person.getId(), ContactChangeType.DOCUMENT_CHANGED,
                previousDocument, document.type() + ":" + document.value());
    }
}

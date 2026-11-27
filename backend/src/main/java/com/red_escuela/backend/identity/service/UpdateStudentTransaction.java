package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.StudentRecordResponse;
import com.red_escuela.backend.identity.dto.UpdateStudentRequest;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentDocumentAlreadyExistsException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.StudentEmailChanger;
import com.red_escuela.backend.shared.domain.DocumentNumber;
import com.red_escuela.backend.shared.domain.DocumentType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateStudentTransaction {
    private final StudentRepository studentRepository;
    private final PersonRepository personRepository;
    private final ContactChangeRecorder contactChangeRecorder;
    private final StudentEmailChanger studentEmailChanger;

    public UpdateStudentTransaction(StudentRepository studentRepository, PersonRepository personRepository,
                                    ContactChangeRecorder contactChangeRecorder, StudentEmailChanger studentEmailChanger) {
        this.studentRepository = studentRepository;
        this.personRepository = personRepository;
        this.contactChangeRecorder = contactChangeRecorder;
        this.studentEmailChanger = studentEmailChanger;
    }

    @Transactional
    public StudentRecordResponse update(Long studentId, UpdateStudentRequest request, boolean includeWithdrawalReason) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Person person = student.getPerson();
        DocumentType documentType = request.documentType() == null ? person.getDocumentType() : request.documentType();
        DocumentNumber document = DocumentNumber.of(documentType, request.documentNumber());
        boolean documentChanged = document.type() != person.getDocumentType()
                || !document.value().equals(person.getDni());
        if (documentChanged) {
            requireDocumentAvailable(person, document);
            String previousDocument = person.getDocumentType() + ":" + person.getDni();
            person.changeDocument(document.type(), document.value());
            contactChangeRecorder.recordChange(person.getId(), actorPersonId(), ContactChangeType.DOCUMENT_CHANGED,
                    previousDocument, document.type() + ":" + document.value());
        }
        person.setName(request.firstName().strip());
        person.setLastname(request.lastName().strip());
        person.setBirthDate(request.birthDate());
        person.setSex(request.sex());
        person.setNationality(request.nationality());
        person.setPhoneNumber(request.phone());
        if (request.email() != null && !request.email().isBlank()) {
            studentEmailChanger.changeByStaff(studentId, person, request.email(), actorPersonId());
        }
        personRepository.save(person);
        return StudentRecordResponse.from(student, includeWithdrawalReason);
    }

    private void requireDocumentAvailable(Person person, DocumentNumber document) {
        personRepository.findByDocumentTypeAndDni(document.type(), document.value())
                .filter(owner -> !owner.getId().equals(person.getId()))
                .ifPresent(owner -> {
                    throw new StudentDocumentAlreadyExistsException(document.type(), document.value());
                });
    }

    private Long actorPersonId() {
        return Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}

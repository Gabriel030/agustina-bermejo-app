package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.DocumentType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoStudentIdentityCompleter {
    private final PersonRepository personRepository;
    private final StudentRoleGranter studentRoleGranter;

    public DemoStudentIdentityCompleter(PersonRepository personRepository, StudentRoleGranter studentRoleGranter) {
        this.personRepository = personRepository;
        this.studentRoleGranter = studentRoleGranter;
    }

    @Transactional
    public void completeIfMissing(String username, String documentNumber) {
        Person person = personRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("No existe la persona demo " + username));
        if (person.getDni() == null) {
            person.changeDocument(DocumentType.DNI, documentNumber);
        }
        studentRoleGranter.grantStudentRoleIfMissing(person);
        personRepository.save(person);
    }
}

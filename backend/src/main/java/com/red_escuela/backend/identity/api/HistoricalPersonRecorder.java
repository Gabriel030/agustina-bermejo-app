package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.HistoricalPersonEmailTakenException;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.DocumentNumber;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.HistoricalDiscardBlockedException;
import com.red_escuela.backend.shared.persistence.InClauseChunks;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalPersonRecorder {
    private static final String STUDENT_ROLE = "ALUMNO";
    private static final String INSTRUCTOR_ROLE = "DOCENTE";
    private static final LocalTime HISTORICAL_TIME = LocalTime.NOON;

    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final RoleRepository roleRepository;
    private final BasePersonCreator basePersonCreator;
    private final InstructorProfileCreationPort instructorProfileCreationPort;

    public HistoricalPersonRecorder(PersonRepository personRepository, StudentRepository studentRepository,
            InstitutionRepository institutionRepository, RoleRepository roleRepository,
            BasePersonCreator basePersonCreator, InstructorProfileCreationPort instructorProfileCreationPort) {
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.roleRepository = roleRepository;
        this.basePersonCreator = basePersonCreator;
        this.instructorProfileCreationPort = instructorProfileCreationPort;
    }

    @Transactional(readOnly = true)
    public boolean documentExists(DocumentType type, String number) {
        return personRepository.findByDocumentTypeAndDni(type, number).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return personRepository.findByEmail(EmailAddress.of(email)).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Long> findIdByDocument(DocumentType type, String number) {
        return personRepository.findByDocumentTypeAndDni(type, number).map(Person::getId);
    }

    @Transactional(readOnly = true)
    public Map<DocumentKey, Long> findStudentIdsByDocuments(Collection<DocumentKey> documents) {
        Map<DocumentType, Set<String>> numbersByType = new HashMap<>();
        documents.forEach(document -> numbersByType.computeIfAbsent(document.type(), type -> new TreeSet<>()).add(document.number()));
        Map<DocumentKey, Long> studentIds = new HashMap<>();
        numbersByType.forEach((type, numbers) -> {
            for (List<String> chunk : InClauseChunks.of(numbers)) {
                studentRepository.findIdsByDocumentTypeAndNumbers(type, chunk)
                        .forEach(row -> studentIds.putIfAbsent(new DocumentKey(type, row.documentNumber()), row.studentId()));
            }
        });
        return studentIds;
    }

    @Transactional
    public Long record(HistoricalPerson historical) {
        DocumentNumber document = DocumentNumber.of(historical.documentType(), historical.documentNumber());
        if (personRepository.findByDocumentTypeAndDni(document.type(), document.value()).isPresent()) {
            throw new DniAlreadyExistsException(document.type(), document.value());
        }
        if (basePersonCreator.isEmailRegistered(historical.email())) {
            throw new HistoricalPersonEmailTakenException();
        }
        Institution institution = institutionRepository.findById(historical.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(historical.institutionId()));

        Person person = basePersonCreator.createBasePerson(historical.firstName(), historical.lastName(),
                historical.email());
        person.changeDocument(document.type(), document.value());
        person.setSex(historical.sex());
        person.setBirthDate(historical.birthDate());
        person.setPhoneNumber(historical.phone());
        person.setNationality(historical.nationality());
        person.setGrantSystemAccess(false);
        person.setEmailVerified(false);
        person.setPassword(null);
        person.setStatus(historical.active() && historical.withdrawnOn() == null
                ? StatusType.ACTIVE
                : StatusType.INACTIVE);
        var address = person.getAddress();
        address.setStreet(historical.street());
        address.setCity(historical.city());
        address.setState(historical.state());
        address.setPostalCode(historical.postalCode());
        String roleName = historical.role() == HistoricalPersonRole.STUDENT ? STUDENT_ROLE : INSTRUCTOR_ROLE;
        Role role = roleRepository.findByName(roleName).orElseThrow(() -> new RoleNotFoundException(roleName));
        person.getRoles().add(role);
        personRepository.save(person);

        if (historical.role() == HistoricalPersonRole.STUDENT) {
            studentRepository.save(newStudent(person, institution, historical));
        } else {
            instructorProfileCreationPort.createInstructorProfile(person);
        }
        return person.getId();
    }

    @Transactional
    public void discardPerson(Long personId) {
        Optional<Person> found = personRepository.findById(personId);
        if (found.isEmpty()) {
            return;
        }
        Person person = found.get();
        if (Boolean.TRUE.equals(person.getGrantSystemAccess()) || person.getPassword() != null) {
            throw new HistoricalDiscardBlockedException("La persona ya tiene acceso al sistema");
        }
        studentRepository.findByPerson_Id(personId).ifPresent(student -> {
            studentRepository.delete(student);
            studentRepository.flush();
        });
        instructorProfileCreationPort.removeInstructorProfile(personId);
        basePersonCreator.deleteBasePerson(person);
    }

    private static Student newStudent(Person person, Institution institution, HistoricalPerson historical) {
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        if (historical.registeredOn() != null) {
            student.setRegisteredAt(historical.registeredOn().atTime(HISTORICAL_TIME));
        }
        if (historical.withdrawnOn() != null) {
            student.setWithdrawnAt(historical.withdrawnOn().atTime(HISTORICAL_TIME));
        }
        GuardianContact guardian = historical.guardian();
        if (guardian != null) {
            student.setGuardianFullName(guardian.fullName());
            student.setGuardianDni(guardian.documentNumber());
            student.setGuardianRelationship(guardian.relationship());
            student.setGuardianPhone(guardian.phone());
            student.setGuardianEmail(guardian.email());
        }
        EmergencyContact emergency = historical.emergencyContact();
        if (emergency != null) {
            student.setEmergencyContactName(emergency.name());
            student.setEmergencyContactRelationship(emergency.relationship());
            student.setEmergencyContactPhone(emergency.phone());
        }
        return student;
    }
}

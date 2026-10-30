package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.exception.ProspectAlreadyConfirmedException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StudentOnboarding {
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final BasePersonCreator basePersonCreator;
    private final StudentRoleGranter studentRoleGranter;

    public StudentOnboarding(StudentRepository studentRepository,
            InstitutionRepository institutionRepository,
            BasePersonCreator basePersonCreator,
            StudentRoleGranter studentRoleGranter) {
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.basePersonCreator = basePersonCreator;
        this.studentRoleGranter = studentRoleGranter;
    }

    @Transactional
    public StudentOnboardingSummary registerProspect(NewProspect prospect) {
        Institution institution = institutionRepository.findById(prospect.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(prospect.institutionId()));

        Person person = basePersonCreator.createBasePerson(prospect.firstName(), prospect.lastName(), prospect.email());
        person.setPhoneNumber(prospect.phoneNumber());

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);

        return StudentOnboardingSummary.of(studentRepository.save(student));
    }

    @Transactional
    public void saveProspectDraft(Long studentId, ProspectDraft draft) {
        Student student = requireStudent(studentId);
        Person person = student.getPerson();

        if (draft.dni() != null) {
            person.changeDocument(draft.documentType(), draft.dni());
        }

        Address address = person.getAddress();
        if (draft.street() != null) address.setStreet(draft.street());
        if (draft.number() != null) address.setNumber(draft.number());
        if (draft.floor() != null) address.setFloor(draft.floor());
        if (draft.city() != null) address.setCity(draft.city());
        if (draft.state() != null) address.setState(draft.state());
        if (draft.postalCode() != null) address.setPostalCode(draft.postalCode());
        if (draft.country() != null) address.setCountry(draft.country());

        flushRejectingDuplicateDocument(student);
    }

    @Transactional
    public StudentOnboardingSummary confirmProspect(Long studentId, ConfirmedProspect confirmation) {
        Student student = requireStudent(studentId);
        if (student.getOnboardingStatus() != StudentOnboardingStatus.PROSPECTO) {
            throw new ProspectAlreadyConfirmedException(studentId);
        }

        Person person = student.getPerson();
        person.changeDocument(confirmation.documentType(), confirmation.dni());
        person.setUsername(person.getDni());

        Address address = person.getAddress();
        address.setStreet(confirmation.street());
        address.setNumber(confirmation.number());
        address.setFloor(confirmation.floor());
        address.setCity(confirmation.city());
        address.setState(confirmation.state());
        address.setPostalCode(confirmation.postalCode());
        address.setCountry(confirmation.country());

        studentRoleGranter.grantStudentRoleIfMissing(person);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);

        flushRejectingDuplicateDocument(student);

        return StudentOnboardingSummary.of(student);
    }

    private Student requireStudent(Long studentId) {
        return studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    private void flushRejectingDuplicateDocument(Student student) {
        try {
            studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            Person person = student.getPerson();
            throw new DniAlreadyExistsException(person.getDocumentType(), person.getDni());
        }
    }
}

package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.identity.domain.LegalAge;
import com.red_escuela.backend.identity.exception.BirthDateRequiredException;
import com.red_escuela.backend.identity.exception.GuardianRequiredForMinorException;
import com.red_escuela.backend.shared.domain.DocumentNumber;
import com.red_escuela.backend.shared.domain.DocumentType;
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
        student.setRegisteredAt(ApplicationTime.now());
        student.setRegisteredByPersonId(prospect.registeredByPersonId());

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

        if (draft.birthDate() != null) person.setBirthDate(draft.birthDate());
        if (draft.sex() != null) person.setSex(draft.sex());
        if (draft.nationality() != null) person.setNationality(draft.nationality());
        applyGuardianDraft(student, draft.guardian());
        applyEmergencyContactDraft(student, draft.emergencyContact());

        flushRejectingDuplicateDocument(student);
    }

    @Transactional
    public StudentOnboardingSummary confirmProspect(Long studentId, ConfirmedProspect confirmation) {
        Student student = requireStudent(studentId);
        if (student.getOnboardingStatus() != StudentOnboardingStatus.PROSPECTO) {
            throw new ProspectAlreadyConfirmedException(studentId);
        }

        if (confirmation.birthDate() == null) {
            throw new BirthDateRequiredException();
        }
        if (LegalAge.isMinor(confirmation.birthDate(), ApplicationTime.today())
                && (isGuardianIncomplete(confirmation.guardian())
                        || isEmergencyContactIncomplete(confirmation.emergencyContact()))) {
            throw new GuardianRequiredForMinorException();
        }

        Person person = student.getPerson();
        person.changeDocument(confirmation.documentType(), confirmation.dni());
        person.setBirthDate(confirmation.birthDate());
        if (confirmation.sex() != null) person.setSex(confirmation.sex());
        if (confirmation.nationality() != null) person.setNationality(confirmation.nationality());
        applyGuardianDraft(student, confirmation.guardian());
        applyEmergencyContactDraft(student, confirmation.emergencyContact());
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

    private static boolean isGuardianIncomplete(GuardianContact guardian) {
        return guardian == null
                || isBlank(guardian.fullName())
                || isBlank(guardian.documentNumber())
                || isBlank(guardian.relationship())
                || isBlank(guardian.phone());
    }

    private static boolean isEmergencyContactIncomplete(EmergencyContact emergencyContact) {
        return emergencyContact == null
                || isBlank(emergencyContact.name())
                || isBlank(emergencyContact.relationship())
                || isBlank(emergencyContact.phone());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void applyGuardianDraft(Student student, GuardianContact guardian) {
        if (guardian == null) return;
        if (guardian.fullName() != null) student.setGuardianFullName(guardian.fullName());
        if (!isBlank(guardian.documentNumber())) {
            student.setGuardianDni(DocumentNumber.of(DocumentType.DNI, guardian.documentNumber()).value());
        }
        if (guardian.relationship() != null) student.setGuardianRelationship(guardian.relationship());
        if (guardian.phone() != null) student.setGuardianPhone(guardian.phone());
        if (guardian.email() != null) student.setGuardianEmail(guardian.email());
    }

    private static void applyEmergencyContactDraft(Student student, EmergencyContact emergencyContact) {
        if (emergencyContact == null) return;
        if (emergencyContact.name() != null) student.setEmergencyContactName(emergencyContact.name());
        if (emergencyContact.relationship() != null) student.setEmergencyContactRelationship(emergencyContact.relationship());
        if (emergencyContact.phone() != null) student.setEmergencyContactPhone(emergencyContact.phone());
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

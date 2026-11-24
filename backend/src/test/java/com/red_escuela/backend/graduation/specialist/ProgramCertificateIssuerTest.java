package com.red_escuela.backend.graduation.specialist;


import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.graduation.repository.CertificateIssuanceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramCertificateIssuerTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramCertificateIssuer certificateService;

    @Autowired
    private CertificateIssuanceRepository certificateIssuanceRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPersonBase(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student persistStudent(String dni) {
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void authenticateAsPerson(String dni) {
        Person persona = persistPersonBase(dni);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persona.getId().toString(), null));
    }

    @Test
    void approvedSubjectsCertificateWithZeroApprovedSubjectsIssuesValidPdfWithoutException() {
        Student student = persistStudent("90000005");
        Program program = persistProgram("CarreraSinAprobadas");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        authenticateAsPerson("ADMIN90000005");

        byte[] pdf = certificateService.issueApprovedSubjectsCertificate(enrollment.getId());
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void approvedSubjectsCertificateListsAnEquivalenceWithTheEquivalenceMark() throws Exception {
        Student student = persistStudent("90000006");
        Program program = persistProgram("CarreraConEquivalencia");
        com.red_escuela.backend.academicstructure.entity.Subject subject = new com.red_escuela.backend.academicstructure.entity.Subject();
        subject.setCode(com.red_escuela.backend.config.TestSubjectCodes.next());
        subject.setName("Algebra Equivalente");
        entityManager.persist(subject);
        com.red_escuela.backend.academicstructure.entity.ProgramSubject programSubject = new com.red_escuela.backend.academicstructure.entity.ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(4);
        programSubject.setSortOrder(0);
        entityManager.persist(programSubject);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        Person actor = persistPersonBase("90000007");
        entityManager.persist(com.red_escuela.backend.enrollment.entity.SubjectEquivalence.builder().programEnrollment(enrollment)
                .subjectId(subject.getId()).originInstitution("Universidad X").originSubjectName("Algebra")
                .recognizedOn(java.time.LocalDate.of(2026, 3, 1)).resolutionNumber("RES-1").reason("Estudios previos")
                .recordedById(actor.getId()).recordedAt(LocalDateTime.now()).build());
        entityManager.flush();
        authenticateAsPerson("ADMIN90000006");

        byte[] pdf = certificateService.issueApprovedSubjectsCertificate(enrollment.getId());

        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document))
                    .contains("Algebra Equivalente (Equivalencia)");
        }
    }
}

package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCohortRecorder;
import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentCohortProgramMismatchException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramEnrollmentCohortProgramMismatchIT extends AbstractIntegrationTest {
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private ProgramApplicationRegistrar programApplicationRegistrar;
    @Autowired
    private ProgramSubjectService programSubjectService;
    @Autowired
    private ProgramCohortRecorder programCohortRecorder;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Program persistProgramWithSubject(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        program.setVisibleInCatalog(true);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia " + name);
        entityManager.persist(subject);
        entityManager.flush();
        programSubjectService.addSubject(program.getId(), new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null));
        return program;
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
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
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private ProgramCohort persistOpenCohort(Program program) {
        Person director = new Person();
        Address address = new Address();
        entityManager.persist(address);
        director.setUsername("director.mismatch." + System.nanoTime());
        director.setDni(Long.toString(System.nanoTime()));
        director.setName("Persona");
        director.setLastname("Prueba");
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);
        com.red_escuela.backend.academicstructure.entity.Term term = com.red_escuela.backend.testsupport.TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-mismatch");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        ProgramCohort cohort = new ProgramCohort();
        cohort.markOpened(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
        cohort.setProgramId(program.getId());
        cohort.setTermId(term.getId());
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.now(clock).minusDays(30));
        cohort.setAdmissionEnd(LocalDate.now(clock).plusDays(30));
        cohort.setCursadaStart(LocalDate.now(clock));
        cohort.setCreatedBy(director);
        return programCohortRecorder.save(cohort);
    }

    @Test
    void directEnrollmentWithCohortOfAnotherProgramIsRejected() {
        Program requested = persistProgramWithSubject("Carrera pedida directa");
        Program other = persistProgramWithSubject("Carrera de la cohorte directa");
        ProgramCohort foreignCohort = persistOpenCohort(other);
        Student student = persistStudent("72000001");
        entityManager.flush();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(requested.getId(), foreignCohort.getId())))
                .isInstanceOf(ProgramEnrollmentCohortProgramMismatchException.class);
    }

    @Test
    void directEnrollmentWithCohortOfTheSameProgramKeepsWorking() {
        Program program = persistProgramWithSubject("Carrera propia directa");
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("72000002");
        entityManager.flush();

        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));

        assertThat(enrollment.getProgram().getId()).isEqualTo(program.getId());
        assertThat(enrollment.getPlan().getId()).isEqualTo(cohort.getPlan().getId());
    }

    @Test
    void applicationWithCohortOfAnotherProgramIsRejected() {
        Program requested = persistProgramWithSubject("Carrera pedida postulación");
        Program other = persistProgramWithSubject("Carrera de la cohorte postulación");
        ProgramCohort foreignCohort = persistOpenCohort(other);
        Student student = persistStudent("72000003");
        entityManager.flush();

        assertThatThrownBy(() -> programApplicationRegistrar.register(student, requested.getId(), foreignCohort.getId()))
                .isInstanceOf(ProgramEnrollmentCohortProgramMismatchException.class);
    }

    @Test
    void applicationWithCohortOfTheSameProgramKeepsWorking() {
        Program program = persistProgramWithSubject("Carrera propia postulación");
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("72000004");
        entityManager.flush();

        var application = programApplicationRegistrar.register(student, program.getId(), cohort.getId());

        assertThat(application.getProgramCohort().getId()).isEqualTo(cohort.getId());
    }
}

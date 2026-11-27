package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCohortRecorder;
import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.CurriculumPlanNotOpenForNewStudentsException;
import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ActivateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.CreateCurriculumPlanUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.dto.ChangeProgramEnrollmentPlanRequest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentCohortRequiredException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentPlanMismatchException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInEnrollmentPlanException;
import com.red_escuela.backend.enrollment.usecase.programenrollment.ChangeProgramEnrollmentPlanCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollment.ChangeProgramEnrollmentPlanUseCase;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramEnrollmentPlanIT extends AbstractIntegrationTest {
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private ProgramSubjectService programSubjectService;
    @Autowired
    private ProgramCohortRecorder programCohortRecorder;
    @Autowired
    private CreateCurriculumPlanUseCase createCurriculumPlanUseCase;
    @Autowired
    private ActivateCurriculumPlanUseCase activateCurriculumPlanUseCase;
    @Autowired
    private ChangeProgramEnrollmentPlanUseCase changeProgramEnrollmentPlanUseCase;
    @Autowired
    private ProgramPlanSubjectGuard programPlanSubjectGuard;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        entityManager.flush();
        return program;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        entityManager.flush();
        return subject;
    }

    private void addSubject(Program program, Subject subject) {
        programSubjectService.addSubject(program.getId(), new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null));
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
        director.setUsername("director.plan." + System.nanoTime());
        director.setDni(Long.toString(System.nanoTime()));
        director.setName("Persona");
        director.setLastname("Prueba");
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);
        com.red_escuela.backend.academicstructure.entity.Term term = com.red_escuela.backend.testsupport.TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-plan");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        ProgramCohort cohort = new ProgramCohort();
        cohort.setProgramId(program.getId());
        cohort.setTermId(term.getId());
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.now(clock).minusDays(30));
        cohort.setAdmissionEnd(LocalDate.now(clock).plusDays(30));
        cohort.setCursadaStart(LocalDate.now(clock));
        cohort.setCreatedBy(director);
        return programCohortRecorder.save(cohort);
    }

    private CurriculumPlanResponse activateNewPlanVersion(Program program, Long copyFromPlanId) {
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(),
                new CreateCurriculumPlanRequest("Plan nuevo", LocalDate.now(clock), null, null, null, copyFromPlanId));
        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();
        return draft;
    }

    @Test
    void enrollingWithoutCohortIsRejectedWithACohortRequiredError() {
        Program program = persistProgram("Carrera sin cohorte");
        Student student = persistStudent("71000001");
        entityManager.flush();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(ProgramEnrollmentCohortRequiredException.class);
    }

    @Test
    void enrollmentInheritsThePlanOfTheCohortEvenAfterANewPlanBecomesCurrent() {
        Program program = persistProgram("Carrera con herencia");
        addSubject(program, persistSubject("Anatomía"));
        ProgramCohort cohort = persistOpenCohort(program);
        Long cohortPlanId = cohort.getPlan().getId();
        Student student = persistStudent("71000002");
        entityManager.flush();

        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));

        assertThat(enrollment.getPlan().getId()).isEqualTo(cohortPlanId);
        assertThat(enrollment.getProgramCohort().getId()).isEqualTo(cohort.getId());
    }

    @Test
    void enrollingInACohortWhoseNewPlanExpiredIsRejected() {
        Program program = persistProgram("Carrera con plan vencido");
        addSubject(program, persistSubject("Química"));
        ProgramCohort cohort = persistOpenCohort(program);
        Long originalPlanId = cohort.getPlan().getId();
        activateNewPlanVersion(program, originalPlanId);
        Student student = persistStudent("71000003");
        entityManager.flush();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId())))
                .isInstanceOf(CurriculumPlanNotOpenForNewStudentsException.class);
    }

    @Test
    void subjectOfAnotherPlanOfTheSameProgramIsConflictForTheEnrollmentPlan() {
        Program program = persistProgram("Carrera con dos planes");
        Subject inFirstPlan = persistSubject("Biología");
        addSubject(program, inFirstPlan);
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("71000004");
        entityManager.flush();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));
        Subject onlyInSecondPlan = persistSubject("Genética");
        CurriculumPlanResponse secondPlan = activateNewPlanVersion(program, cohort.getPlan().getId());
        programSubjectService.addSubjectToPlan(secondPlan.id(), new CreateProgramSubjectRequest(
                onlyInSecondPlan.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null));
        entityManager.flush();

        assertThatThrownBy(() -> programPlanSubjectGuard.requireSubjectInEnrollmentPlan(enrollment, onlyInSecondPlan.getId()))
                .isInstanceOf(SubjectNotInEnrollmentPlanException.class);
        programPlanSubjectGuard.requireSubjectInEnrollmentPlan(enrollment, inFirstPlan.getId());
    }

    @Test
    void secretariatChangesThePlanOfTheEnrollmentOnlyThroughThePlanChangeAction() {
        Program program = persistProgram("Carrera con cambio de plan");
        addSubject(program, persistSubject("Fisiología"));
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("71000005");
        entityManager.flush();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));
        CurriculumPlanResponse secondPlan = activateNewPlanVersion(program, cohort.getPlan().getId());

        var response = changeProgramEnrollmentPlanUseCase.change(new ChangeProgramEnrollmentPlanCommand(
                enrollment.getId(), new ChangeProgramEnrollmentPlanRequest(secondPlan.id(), List.of())));
        entityManager.flush();
        entityManager.clear();

        assertThat(response.planId()).isEqualTo(secondPlan.id());
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment.getId()).getPlan().getId()).isEqualTo(secondPlan.id());
    }

    @Test
    void planChangeToAPlanOfAnotherProgramIsRejected() {
        Program program = persistProgram("Carrera origen");
        addSubject(program, persistSubject("Histología"));
        Program otherProgram = persistProgram("Carrera ajena");
        addSubject(otherProgram, persistSubject("Patología"));
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("71000006");
        entityManager.flush();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));
        Long foreignPlanId = persistOpenCohort(otherProgram).getPlan().getId();

        assertThatThrownBy(() -> changeProgramEnrollmentPlanUseCase.change(new ChangeProgramEnrollmentPlanCommand(
                enrollment.getId(), new ChangeProgramEnrollmentPlanRequest(foreignPlanId, List.of()))))
                .isInstanceOf(ProgramEnrollmentPlanMismatchException.class);
    }
}

package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCohortRecorder;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.enrollment.api.PrerequisiteBatchQuery;
import com.red_escuela.backend.enrollment.api.PrerequisiteBatchResult;
import com.red_escuela.backend.enrollment.api.PrerequisiteDetail;
import com.red_escuela.backend.enrollment.api.PrerequisiteOutcome;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteBatchEvaluator;
import com.red_escuela.backend.enrollment.dto.OwnSubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import java.time.LocalDateTime;

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
class PlanOfEnrollmentReadersIT extends AbstractIntegrationTest {
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
    private ProgramPrerequisiteBatchEvaluator prerequisiteBatchEvaluator;
    @Autowired
    private SubjectEquivalenceReader subjectEquivalenceReader;
    @Autowired
    private ProgramCurriculumFinder programCurriculumFinder;
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

    private CurriculumPlanResponse activateNewPlanVersion(Program program, Long copyFromPlanId) {
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(),
                new CreateCurriculumPlanRequest("Plan nuevo", LocalDate.now(clock), null, null, null, copyFromPlanId));
        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();
        return draft;
    }

    private ProgramSubject addToProgram(Program program, Subject subject, int year) {
        return programSubjectService.addSubject(program.getId(), new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, year, CurricularTerm.FIRST, 10, null, null, null));
    }

    private void startNewPlanVersionWithout(Program program, Long copyFromPlanId, Subject droppedSubject, Subject droppedPrerequisiteOwner) {
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(),
                new CreateCurriculumPlanRequest("Plan nuevo", LocalDate.now(clock), null, null, null, copyFromPlanId));
        if (droppedPrerequisiteOwner != null) {
            Long entryId = programCurriculumFinder.findProgramSubjectOfPlan(draft.id(), droppedPrerequisiteOwner.getId()).orElseThrow().id();
            programSubjectService.listPrerequisites(entryId).forEach(prerequisite -> programSubjectService.removePrerequisite(prerequisite.getId()));
        }
        if (droppedSubject != null) {
            programSubjectService.deactivate(programCurriculumFinder.findProgramSubjectOfPlan(draft.id(), droppedSubject.getId()).orElseThrow().id());
        }
        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();
    }

    @Test
    void prerequisitesAreReadFromThePlanOfTheEnrollmentNotFromTheCurrentPlanOfTheProgram() {
        Program program = persistProgram("Carrera con correlativa quitada");
        Subject algebra = persistSubject("Álgebra");
        Subject analysis = persistSubject("Análisis");
        ProgramSubject algebraEntry = addToProgram(program, algebra, 1);
        ProgramSubject analysisEntry = addToProgram(program, analysis, 2);
        programSubjectService.addPrerequisite(analysisEntry.getId(),
                new AddPrerequisiteRequest(algebraEntry.getId(), RequirementType.APPROVED, null, null));
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("72000001");
        entityManager.flush();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));
        startNewPlanVersionWithout(program, cohort.getPlan().getId(), null, analysis);

        PrerequisiteBatchResult result = prerequisiteBatchEvaluator.assessAll(
                List.of(new PrerequisiteBatchQuery(1L, enrollment.getId(), analysis.getId()))).get(1L);

        assertThat(result.details()).extracting(PrerequisiteDetail::subjectId).containsExactly(algebra.getId());
        assertThat(result.outcome()).isEqualTo(PrerequisiteOutcome.UNMET);
    }

    @Test
    void subjectOnlyInTheCurrentPlanHasNoPrerequisitesForAnEnrollmentOfTheOlderPlan() {
        Program program = persistProgram("Carrera con materia nueva");
        Subject algebra = persistSubject("Álgebra");
        addToProgram(program, algebra, 1);
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("72000003");
        entityManager.flush();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));
        Subject onlyInNewPlan = persistSubject("Estadística");
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(),
                new CreateCurriculumPlanRequest("Plan nuevo", LocalDate.now(clock), null, null, null, cohort.getPlan().getId()));
        ProgramSubject newEntry = programSubjectService.addSubjectToPlan(draft.id(), new CreateProgramSubjectRequest(
                onlyInNewPlan.getId(), ProgramSubjectCategory.MANDATORY, 2, CurricularTerm.FIRST, 10, null, null, null));
        Long algebraEntryInNewPlan = programCurriculumFinder.findProgramSubjectOfPlan(draft.id(), algebra.getId()).orElseThrow().id();
        programSubjectService.addPrerequisite(newEntry.getId(),
                new AddPrerequisiteRequest(algebraEntryInNewPlan, RequirementType.APPROVED, null, null));
        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();

        PrerequisiteBatchResult result = prerequisiteBatchEvaluator.assessAll(
                List.of(new PrerequisiteBatchQuery(1L, enrollment.getId(), onlyInNewPlan.getId()))).get(1L);

        assertThat(result.details()).isEmpty();
    }

    @Test
    void equivalenceLabelsComeFromThePlanOfTheEnrollmentEvenWhenTheCurrentPlanDroppedTheSubject() {
        Program program = persistProgram("Carrera con materia dada de baja");
        Subject biology = persistSubject("Biología");
        addToProgram(program, biology, 1);
        ProgramCohort cohort = persistOpenCohort(program);
        Student student = persistStudent("72000002");
        entityManager.flush();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));
        SubjectEquivalence equivalence = new SubjectEquivalence();
        equivalence.setProgramEnrollment(enrollment);
        equivalence.setSubjectId(biology.getId());
        equivalence.setOriginInstitution("Otra institución");
        equivalence.setOriginSubjectName("Biología I");
        equivalence.setRecognizedOn(LocalDate.now(clock));
        equivalence.setResolutionNumber("R-1");
        equivalence.setReason("Equivalencia");
        equivalence.setRecordedById(student.getPerson().getId());
        equivalence.setRecordedAt(LocalDateTime.now(clock));
        entityManager.persist(equivalence);
        startNewPlanVersionWithout(program, cohort.getPlan().getId(), biology, null);
        entityManager.flush();
        entityManager.clear();

        List<OwnSubjectEquivalenceResponse> own = subjectEquivalenceReader.listOwn(student.getId(), enrollment.getId());

        assertThat(own).hasSize(1);
        assertThat(own.get(0).subjectName()).isEqualTo("Biología");
        assertThat(own.get(0).inCurrentPlan()).isTrue();
    }
}

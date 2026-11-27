package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.testsupport.ActiveProgramSectionTestFactory;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.NotSectionLeadInstructorException;
import com.red_escuela.backend.evaluation.exception.SectionEvaluationConfigNotFoundException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseSectionEvaluationConfigUseCaseTest extends AbstractIntegrationTest {

    @Autowired
    private GetMySectionEvaluationConfigUseCase getMyConfig;

    @Autowired
    private GetSectionEvaluationConfigForStaffUseCase getConfigForStaff;

    @Autowired
    private SaveSectionEvaluationConfigDraftUseCase saveDraft;

    @Autowired
    private SubmitSectionEvaluationConfigForApprovalUseCase submitForApproval;

    @Autowired
    private ApproveSectionEvaluationConfigUseCase approveConfig;

    @Autowired
    private RejectSectionEvaluationConfigUseCase rejectConfig;

    @Autowired
    private ListCourseSectionEvaluationConfigsUseCase listCourseConfigs;

    @Autowired
    private ListSectionEvaluationConfigsUseCase listProgramConfigs;

    @Autowired
    private ListMyCourseSectionEvaluationConfigsUseCase listMyCourseConfigs;

    @Autowired
    private SectionEvaluationSetupPort evaluationSetupPort;

    @Autowired
    private ActiveProgramSectionTestFactory sectionFactory;

    @Autowired
    private SectionEvaluationConfigRepository configRepository;

    @Autowired
    private ExamBoardRepository examBoardRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        return person;
    }

    private Instructor persistInstructor(String dni) {
        Instructor instructor = new Instructor();
        instructor.setPerson(persistPerson(dni, "DOCENTE"));
        entityManager.persist(instructor);
        return instructor;
    }

    private Term persistTerm() {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.now().plusDays(30));
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);
        return term;
    }

    private CourseSubject persistCourseSubject(Term term, String moduleName) {
        Course course = new Course();
        course.setName("Curso " + moduleName);
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName(moduleName);
        entityManager.persist(courseSubject);
        return courseSubject;
    }

    private CourseSubjectSection createCourseSection(CourseSubject courseSubject, Term term) {
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión Curso");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        evaluationSetupPort.createInitialEvaluationDraftForCourseSection(section);
        entityManager.flush();
        return section;
    }

    private void assignInstructor(CourseSubjectSection section, Instructor instructor, InstructorRole role) {
        CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(role);
        entityManager.persist(assignment);
        entityManager.flush();
    }

    private LocalDate nonHolidayDate(int daysFromNow) {
        LocalDate date = LocalDate.now().plusDays(daysFromNow);
        while (holidayRepository.existsByDate(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    private UpdateSectionEvaluationConfigRequest validRequest() {
        return new UpdateSectionEvaluationConfigRequest(2, nonHolidayDate(40), null, null,
                nonHolidayDate(45), nonHolidayDate(60), null, null, nonHolidayDate(80), nonHolidayDate(92));
    }

    @Test
    void initialDraftForACourseSectionIsOwnedByTheCourseSection() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloAuto"), term);

        SectionEvaluationConfigResponse config = getConfigForStaff.get(SectionRef.course(section.getId()));

        assertThat(config.status()).isEqualTo(SectionEvaluationConfigStatus.DRAFT.name());
        assertThat(config.courseSectionId()).isEqualTo(section.getId());
        assertThat(config.subjectSectionId()).isNull();
        assertThat(config.sectionKind()).isEqualTo("COURSE");
        assertThat(config.subjectName()).isEqualTo("ModuloAuto");
        assertThat(configRepository.findByCourseSectionId(section.getId())).isPresent();
    }

    @Test
    void courseConfigGoesFromDraftThroughSubmissionToApprovalAndCreatesFinalCalls() {
        Term term = persistTerm();
        CourseSubject courseSubject = persistCourseSubject(term, "ModuloAprobado");
        CourseSubjectSection section = createCourseSection(courseSubject, term);
        Instructor lead = persistInstructor("91000001");
        assignInstructor(section, lead, InstructorRole.LEAD);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persistPerson("91000002", "DIRECTOR").getId().toString(), null));
        SectionRef ref = SectionRef.course(section.getId());

        SectionEvaluationConfigResponse saved = saveDraft.save(
                new SaveSectionEvaluationConfigDraftCommand(ref, lead.getId(), validRequest()));
        assertThat(saved.canEdit()).isTrue();
        SectionEvaluationConfigResponse submitted = submitForApproval.submit(
                new SubmitSectionEvaluationConfigForApprovalCommand(ref, lead.getId()));
        assertThat(submitted.status()).isEqualTo(SectionEvaluationConfigStatus.PENDING_APPROVAL.name());
        SectionEvaluationConfigResponse approved = approveConfig.approve(new ApproveSectionEvaluationConfigCommand(ref));

        assertThat(approved.status()).isEqualTo(SectionEvaluationConfigStatus.APPROVED.name());
        assertThat(approved.reviewedAt()).isNotNull();
        List<ExamBoard> examBoards = examBoardRepository.findByCourseSubjectIdAndTermId(courseSubject.getId(), term.getId());
        assertThat(examBoards).hasSize(2);
    }

    @Test
    void rejectedCourseConfigKeepsTheReasonAndCanBeResubmitted() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloRechazado"), term);
        Instructor lead = persistInstructor("91000003");
        assignInstructor(section, lead, InstructorRole.LEAD);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persistPerson("91000004", "DIRECTOR").getId().toString(), null));
        SectionRef ref = SectionRef.course(section.getId());
        saveDraft.save(new SaveSectionEvaluationConfigDraftCommand(ref, lead.getId(), validRequest()));
        submitForApproval.submit(new SubmitSectionEvaluationConfigForApprovalCommand(ref, lead.getId()));

        SectionEvaluationConfigResponse rejected = rejectConfig.reject(
                new RejectSectionEvaluationConfigCommand(ref, new RejectSectionEvaluationConfigRequest("Fechas")));
        SectionEvaluationConfigResponse resubmitted = submitForApproval.submit(
                new SubmitSectionEvaluationConfigForApprovalCommand(ref, lead.getId()));

        assertThat(rejected.status()).isEqualTo(SectionEvaluationConfigStatus.REJECTED.name());
        assertThat(rejected.rejectionReason()).isEqualTo("Fechas");
        assertThat(resubmitted.status()).isEqualTo(SectionEvaluationConfigStatus.PENDING_APPROVAL.name());
    }

    @Test
    void onlyTheCourseSectionLeadCanSaveTheDraft() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloLider"), term);
        Instructor assistant = persistInstructor("91000005");
        Instructor outsider = persistInstructor("91000006");
        assignInstructor(section, assistant, InstructorRole.ASSISTANT);
        SectionRef ref = SectionRef.course(section.getId());

        assertThatThrownBy(() -> saveDraft.save(
                new SaveSectionEvaluationConfigDraftCommand(ref, assistant.getId(), validRequest())))
                .isInstanceOf(NotSectionLeadInstructorException.class);
        assertThatThrownBy(() -> saveDraft.save(
                new SaveSectionEvaluationConfigDraftCommand(ref, outsider.getId(), validRequest())))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void programSectionIdDoesNotResolveACourseConfig() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloAislado"), term);

        assertThatThrownBy(() -> getConfigForStaff.get(SectionRef.program(section.getId())))
                .isInstanceOf(SectionEvaluationConfigNotFoundException.class);
    }

    @Test
    void courseListsReturnOnlyCourseConfigsAndProgramListExcludesThem() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloLista"), term);
        Instructor lead = persistInstructor("91000007");
        assignInstructor(section, lead, InstructorRole.LEAD);
        entityManager.flush();
        entityManager.clear();

        List<SectionEvaluationConfigResponse> draftCourseConfigs = listCourseConfigs.list("DRAFT");
        List<SectionEvaluationConfigResponse> defaultCourseConfigs = listCourseConfigs.list(null);
        List<SectionEvaluationConfigResponse> programDrafts = listProgramConfigs.list(SectionEvaluationConfigStatus.DRAFT);
        List<SectionEvaluationConfigResponse> mine = listMyCourseConfigs.list(lead.getId());

        assertThat(draftCourseConfigs).extracting(SectionEvaluationConfigResponse::courseSectionId).contains(section.getId());
        assertThat(defaultCourseConfigs).extracting(SectionEvaluationConfigResponse::courseSectionId).doesNotContain(section.getId());
        assertThat(programDrafts).extracting(SectionEvaluationConfigResponse::sectionKind).doesNotContain("COURSE");
        assertThat(mine).extracting(SectionEvaluationConfigResponse::courseSectionId).containsExactly(section.getId());
        assertThat(mine.get(0).canEdit()).isTrue();
    }

    @Test
    void configWithoutOwnerIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into section_evaluation_config (status) values ('DRAFT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void configWithBothOwnersIsRejectedByTheDatabase() {
        Term term = persistTerm();
        CourseSubjectSection courseSection = createCourseSection(persistCourseSubject(term, "ModuloXor"), term);
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("MateriaXor");
        entityManager.persist(subject);
        entityManager.flush();
        SubjectSection programSection = sectionFactory.create(subject, term, "Comisión A", 30, null);
        entityManager.flush();

        jdbcTemplate.update("delete from section_evaluation_config where subject_section_id = ?", programSection.getId());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update section_evaluation_config set subject_section_id = ? where course_section_id = ?",
                programSection.getId(), courseSection.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void courseSectionCannotHaveTwoConfigs() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloUnico"), term);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into section_evaluation_config (status, course_section_id) values ('DRAFT', ?)", section.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void getMyConfigReturnsCourseConfigForAssignedInstructor() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSection(persistCourseSubject(term, "ModuloMio"), term);
        Instructor lead = persistInstructor("91000008");
        assignInstructor(section, lead, InstructorRole.LEAD);

        SectionEvaluationConfigResponse config = getMyConfig.get(
                new GetMySectionEvaluationConfigCommand(SectionRef.course(section.getId()), lead.getId()));

        assertThat(config.canEdit()).isTrue();
        assertThat(config.instructors()).hasSize(1);
        assertThat(config.evaluationConfigDeadline()).isNotNull();
    }

    @Test
    void readingAnExistingCourseSectionWithoutConfigCreatesTheDraftLazily() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSectionWithoutConfig(persistCourseSubject(term, "ModuloPerezoso"), term);
        assertThat(configRepository.findByCourseSectionId(section.getId())).isEmpty();

        SectionEvaluationConfigResponse config = getConfigForStaff.get(SectionRef.course(section.getId()));

        assertThat(config.status()).isEqualTo(SectionEvaluationConfigStatus.DRAFT.name());
        assertThat(config.courseSectionId()).isEqualTo(section.getId());
        assertThat(configRepository.findByCourseSectionId(section.getId())).isPresent();
    }

    @Test
    void instructorReadingACourseSectionWithoutConfigGetsAnEditableDraft() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSectionWithoutConfig(persistCourseSubject(term, "ModuloPerezosoDocente"), term);
        Instructor lead = persistInstructor("91000009");
        assignInstructor(section, lead, InstructorRole.LEAD);

        SectionEvaluationConfigResponse config = getMyConfig.get(
                new GetMySectionEvaluationConfigCommand(SectionRef.course(section.getId()), lead.getId()));

        assertThat(config.status()).isEqualTo(SectionEvaluationConfigStatus.DRAFT.name());
        assertThat(config.canEdit()).isTrue();
    }

    @Test
    void lazyDraftIsCreatedOnlyOnceForACourseSection() {
        Term term = persistTerm();
        CourseSubjectSection section = createCourseSectionWithoutConfig(persistCourseSubject(term, "ModuloPerezosoUnico"), term);

        SectionEvaluationConfigResponse first = getConfigForStaff.get(SectionRef.course(section.getId()));
        SectionEvaluationConfigResponse second = getConfigForStaff.get(SectionRef.course(section.getId()));

        assertThat(second.id()).isEqualTo(first.id());
    }

    private CourseSubjectSection createCourseSectionWithoutConfig(CourseSubject courseSubject, Term term) {
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión Curso");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        entityManager.flush();
        return section;
    }
}

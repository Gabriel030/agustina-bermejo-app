package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureStatusFact;
import com.red_escuela.backend.evaluation.domain.CommissionClosureListFilter;
import com.red_escuela.backend.evaluation.dto.CommissionClosureListItemResponse;
import com.red_escuela.backend.evaluation.dto.CommissionClosurePageResponse;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommissionClosureListReaderIT extends AbstractIntegrationTest {

    private static final LocalDateTime PAST = LocalDateTime.of(2020, 6, 30, 12, 0);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private CommissionClosureListReader listReader;
    @Autowired
    private ObjectMapper objectMapper;

    private int sequence = 83000000;
    private int reportSequence = 1;
    private Institution institution;
    private Program program;

    private Term persistTerm(String description, LocalDateTime endDate) {
        Term term = TermFixtures.newTerm();
        term.setYear(2020);
        term.setDescription(description);
        term.setStatus(StatusType.ACTIVE);
        term.setEndDate(endDate);
        entityManager.persist(term);
        return term;
    }

    private Program program() {
        if (program == null) {
            institution = new Institution();
            institution.setName("List institute");
            entityManager.persist(institution);
            program = new Program();
            program.setName("List program");
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);
        }
        return program;
    }

    private SubjectSection persistProgramSection(Term term, String subjectName, String sectionName) {
        program();
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(subjectName);
        entityManager.persist(subject);
        SubjectSection section = new SubjectSection();
        section.setName(sectionName);
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private CourseSubjectSection persistCourseSection(Term term, String moduleName, String sectionName) {
        Course course = new Course();
        course.setName("List course");
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName(moduleName);
        entityManager.persist(courseSubject);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName(sectionName);
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private CommissionClosure persistClosure(SectionKind kind, Long sectionId, ClosureStatus status) {
        CommissionClosure closure = CommissionClosure.builder().sectionKind(kind).sectionId(sectionId).status(status)
                .lockVersion(0L).build();
        entityManager.persist(closure);
        return closure;
    }

    private SubjectSectionEnrollment persistEnrollment(SubjectSection section, EnrollmentStatus status) {
        Person person = persistPerson("Student");
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Person persistPerson(String name) {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private void persistSnapshot(Long closureId, Long enrollmentId, ClosureFinalCondition condition, Boolean correlativesOk) {
        entityManager.persist(CommissionClosureStudent.builder().closureId(closureId).enrollmentId(enrollmentId)
                .finalCondition(condition).promotionGrade(Grade.of(BigDecimal.valueOf(8))).correlativesOk(correlativesOk).build());
    }

    private CommissionClosurePageResponse list(ClosureStatus status, Term term, String query, int page, int size) {
        entityManager.flush();
        entityManager.clear();
        return listReader.readPage(new CommissionClosureListFilter(status, term.getId(), query), page, size);
    }

    @Test
    void sectionWithoutClosureRowAppearsAsPendingFromTheTeacher() {
        Term term = persistTerm("2020-1C", PAST);
        SubjectSection section = persistProgramSection(term, "Anatomia", "Comision A");

        CommissionClosurePageResponse page = list(null, term, null, 0, 10);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.sectionKind()).isEqualTo(ClosureSectionKind.PROGRAM);
            assertThat(item.sectionId()).isEqualTo(section.getId());
            assertThat(item.closureId()).isNull();
            assertThat(item.status()).isEqualTo(ClosureStatus.DRAFT_AVAILABLE);
            assertThat(item.statusFact()).isEqualTo(ClosureStatusFact.NOT_DELIVERED);
            assertThat(item.statusDate()).isNull();
            assertThat(item.promoteCount()).isNull();
            assertThat(item.toReviewCount()).isNull();
            assertThat(item.reportNumber()).isNull();
            assertThat(item.termLabel()).isEqualTo("2020-1C");
        });
        assertThat(page.counts().draftAvailable()).isEqualTo(1);
    }

    @Test
    void sectionsOfTermsThatHaveNotEndedAreNotListed() {
        Term ended = persistTerm("ended", PAST);
        Term running = persistTerm("running", LocalDateTime.now().plusDays(30));
        persistProgramSection(ended, "Ended subject", "Comision A");
        persistProgramSection(running, "Running subject", "Comision A");

        assertThat(list(null, ended, null, 0, 10).items()).hasSize(1);
        assertThat(list(null, running, null, 0, 10).items()).isEmpty();
    }

    @Test
    void courseSectionsAreListedWithTheModuleName() {
        Term term = persistTerm("course term", PAST);
        CourseSubjectSection section = persistCourseSection(term, "Modulo RCP", "Comision unica");

        CommissionClosurePageResponse page = list(null, term, null, 0, 10);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.sectionKind()).isEqualTo(ClosureSectionKind.COURSE);
            assertThat(item.sectionId()).isEqualTo(section.getId());
            assertThat(item.subjectName()).isEqualTo("Modulo RCP");
        });
    }

    @Test
    void itemsAreOrderedConfirmedReopenedPendingClosedAndNewestFirstWithinAGroup() {
        Term term = persistTerm("order", PAST);
        persistProgramSection(term, "Pending", "A");
        SubjectSection closedOld = persistProgramSection(term, "Closed old", "A");
        SubjectSection closedNew = persistProgramSection(term, "Closed new", "A");
        SubjectSection reopened = persistProgramSection(term, "Reopened", "A");
        SubjectSection confirmedOld = persistProgramSection(term, "Confirmed old", "A");
        SubjectSection confirmedNew = persistProgramSection(term, "Confirmed new", "A");
        closed(closedOld, PAST.plusDays(1));
        closed(closedNew, PAST.plusDays(5));
        CommissionClosure reopenedClosure = persistClosure(SectionKind.PROGRAM, reopened.getId(), ClosureStatus.REOPENED);
        persistAudit(reopenedClosure.getId(), ClosureAuditAction.REOPENED, PAST.plusDays(9));
        confirmed(confirmedOld, PAST.plusDays(2));
        confirmed(confirmedNew, PAST.plusDays(3));

        CommissionClosurePageResponse page = list(null, term, null, 0, 10);

        assertThat(page.items()).extracting(CommissionClosureListItemResponse::subjectName)
                .containsExactly("Confirmed new", "Confirmed old", "Reopened", "Pending", "Closed new", "Closed old");
        assertThat(page.items().get(2).statusDate()).isEqualTo(PAST.plusDays(9));
        assertThat(page.items().get(2).statusFact()).isEqualTo(ClosureStatusFact.REOPENED);
    }

    @Test
    void closedItemExposesTheReportAndTheBookAndFolio() {
        Term term = persistTerm("closed", PAST);
        SubjectSection section = persistProgramSection(term, "Closed", "A");
        CommissionClosure closure = closed(section, PAST.plusDays(4));

        CommissionClosureListItemResponse item = list(null, term, null, 0, 10).items().get(0);

        assertThat(item.closureId()).isEqualTo(closure.getId());
        assertThat(item.statusFact()).isEqualTo(ClosureStatusFact.CLOSED);
        assertThat(item.reportNumber()).isEqualTo(closure.getReportNumber());
        assertThat(item.book()).isEqualTo("L1");
        assertThat(item.folio()).isEqualTo("F1");
        assertThat(item.statusDate()).isEqualTo(PAST.plusDays(4));
    }

    @Test
    void countsIgnoreTheStatusFilterButTheListAndTotalRespectIt() {
        Term term = persistTerm("counts", PAST);
        persistProgramSection(term, "Pending one", "A");
        persistProgramSection(term, "Pending two", "A");
        closed(persistProgramSection(term, "Closed one", "A"), PAST);
        confirmed(persistProgramSection(term, "Confirmed one", "A"), PAST);

        CommissionClosurePageResponse page = list(ClosureStatus.DRAFT_AVAILABLE, term, null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).hasSize(2).allSatisfy(item -> assertThat(item.status()).isEqualTo(ClosureStatus.DRAFT_AVAILABLE));
        assertThat(page.counts().all()).isEqualTo(4);
        assertThat(page.counts().draftAvailable()).isEqualTo(2);
        assertThat(page.counts().closed()).isEqualTo(1);
        assertThat(page.counts().teacherConfirmed()).isEqualTo(1);
        assertThat(page.counts().reopened()).isZero();
    }

    @Test
    void countsRespectTheSearchAndTheTermFilters() {
        Term term = persistTerm("search", PAST);
        Term otherTerm = persistTerm("other", PAST);
        persistProgramSection(term, "Nutricion clinica", "Comision A");
        closed(persistProgramSection(term, "Anatomia", "Comision B"), PAST);
        closed(persistProgramSection(otherTerm, "Nutricion otra", "Comision A"), PAST);

        CommissionClosurePageResponse page = list(null, term, "nutri", 0, 10);

        assertThat(page.counts().all()).isEqualTo(1);
        assertThat(page.counts().closed()).isZero();
        assertThat(page.counts().draftAvailable()).isEqualTo(1);
        assertThat(page.items()).extracting(CommissionClosureListItemResponse::subjectName).containsExactly("Nutricion clinica");
    }

    @Test
    void searchMatchesSubjectOrSectionIgnoringAccentsAndCase() {
        Term term = persistTerm("accents", PAST);
        persistProgramSection(term, "Epidemiología", "Comisión Noche");
        persistProgramSection(term, "Otra materia", "Comisión Mañana");

        assertThat(list(null, term, "EPIDEMIOLOGIA", 0, 10).items()).hasSize(1);
        assertThat(list(null, term, "comision manana", 0, 10).items()).extracting(CommissionClosureListItemResponse::subjectName)
                .containsExactly("Otra materia");
        assertThat(list(null, term, "100%", 0, 10).items()).isEmpty();
    }

    @Test
    void pagesSplitTheListAndKeepTheFullTotal() {
        Term term = persistTerm("paging", PAST);
        for (int index = 0; index < 5; index++) {
            persistProgramSection(term, "Subject " + index, "A");
        }

        CommissionClosurePageResponse second = list(null, term, null, 1, 2);

        assertThat(second.items()).extracting(CommissionClosureListItemResponse::subjectName).containsExactly("Subject 2", "Subject 3");
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(list(null, term, null, 9, 2).items()).isEmpty();
    }

    @Test
    void studentPromoteAndReviewCountsComeFromTheSnapshotAndSkipWithdrawn() {
        Term term = persistTerm("snapshot", PAST);
        SubjectSection section = persistProgramSection(term, "Snapshot", "A");
        SubjectSectionEnrollment promotes = persistEnrollment(section, EnrollmentStatus.IN_PROGRESS);
        SubjectSectionEnrollment conditional = persistEnrollment(section, EnrollmentStatus.IN_PROGRESS);
        SubjectSectionEnrollment unmetPrerequisite = persistEnrollment(section, EnrollmentStatus.IN_PROGRESS);
        persistEnrollment(section, EnrollmentStatus.WITHDRAWN);
        CommissionClosure closure = persistClosure(SectionKind.PROGRAM, section.getId(), ClosureStatus.TEACHER_CONFIRMED);
        persistSnapshot(closure.getId(), promotes.getId(), ClosureFinalCondition.PROMOTES, true);
        persistSnapshot(closure.getId(), conditional.getId(), ClosureFinalCondition.PROMOTION_CONDITIONAL, true);
        persistSnapshot(closure.getId(), unmetPrerequisite.getId(), ClosureFinalCondition.GOES_TO_FINAL, false);

        CommissionClosureListItemResponse item = list(null, term, null, 0, 10).items().get(0);

        assertThat(item.studentCount()).isEqualTo(3);
        assertThat(item.promoteCount()).isEqualTo(1);
        assertThat(item.toReviewCount()).isEqualTo(2);
    }

    @Test
    void draftWithSnapshotHidesThePromoteCountButKeepsTheReviewCount() {
        Term term = persistTerm("draft snapshot", PAST);
        SubjectSection section = persistProgramSection(term, "Draft", "A");
        SubjectSectionEnrollment enrollment = persistEnrollment(section, EnrollmentStatus.IN_PROGRESS);
        CommissionClosure closure = persistClosure(SectionKind.PROGRAM, section.getId(), ClosureStatus.DRAFT_AVAILABLE);
        persistSnapshot(closure.getId(), enrollment.getId(), ClosureFinalCondition.PROMOTION_CONDITIONAL, true);

        CommissionClosureListItemResponse item = list(null, term, null, 0, 10).items().get(0);

        assertThat(item.promoteCount()).isNull();
        assertThat(item.toReviewCount()).isEqualTo(1);
    }

    @Test
    void instructorNamesListTheLeadFirst() {
        Term term = persistTerm("instructors", PAST);
        SubjectSection section = persistProgramSection(term, "Taught", "A");
        assign(section, persistPerson("Assistant"), InstructorRole.ASSISTANT);
        assign(section, persistPerson("Lead"), InstructorRole.LEAD);

        CommissionClosureListItemResponse item = list(null, term, null, 0, 10).items().get(0);

        assertThat(item.instructorNames()).hasSize(2);
        assertThat(item.instructorNames().get(0)).startsWith("Lead ");
        assertThat(item.instructorNames().get(1)).startsWith("Assistant ");
    }

    @Test
    void queryCountDoesNotGrowWithTheNumberOfSections() {
        Term term = persistTerm("query count", PAST);
        for (int index = 0; index < 12; index++) {
            SubjectSection section = persistProgramSection(term, "Subject " + index, "A");
            assign(section, persistPerson("Teacher"), InstructorRole.LEAD);
            SubjectSectionEnrollment enrollment = persistEnrollment(section, EnrollmentStatus.IN_PROGRESS);
            CommissionClosure closure = persistClosure(SectionKind.PROGRAM, section.getId(), ClosureStatus.TEACHER_CONFIRMED);
            persistSnapshot(closure.getId(), enrollment.getId(), ClosureFinalCondition.PROMOTES, true);
        }
        entityManager.flush();
        entityManager.clear();

        CommissionClosurePageResponse page = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, 2,
                () -> listReader.readPage(new CommissionClosureListFilter(null, term.getId(), null), 0, 50));

        assertThat(page.items()).hasSize(12);
    }

    @Test
    void listDoesNotExposeAmountsOrPaymentDetails() throws Exception {
        Term term = persistTerm("privacy", PAST);
        persistProgramSection(term, "Private", "A");

        String json = objectMapper.writeValueAsString(list(null, term, null, 0, 10));

        assertThat(json).doesNotContainIgnoringCase("amount").doesNotContainIgnoringCase("debt")
                .doesNotContainIgnoringCase("balance").doesNotContainIgnoringCase("payment");
    }

    private CommissionClosure closed(SubjectSection section, LocalDateTime closedAt) {
        CommissionClosure closure = CommissionClosure.builder().sectionKind(SectionKind.PROGRAM).sectionId(section.getId())
                .status(ClosureStatus.CLOSED).reportNumber("ACTA-2020-" + (9000 + reportSequence++)).book("L1").folio("F1").closedById(persistPerson("Closer").getId()).closedAt(closedAt)
                .lockVersion(0L).build();
        entityManager.persist(closure);
        return closure;
    }

    private void confirmed(SubjectSection section, LocalDateTime confirmedAt) {
        entityManager.persist(CommissionClosure.builder().sectionKind(SectionKind.PROGRAM).sectionId(section.getId())
                .status(ClosureStatus.TEACHER_CONFIRMED).confirmedAt(confirmedAt).lockVersion(0L).build());
    }

    private void persistAudit(Long closureId, ClosureAuditAction action, LocalDateTime at) {
        entityManager.persist(CommissionClosureAudit.builder().closureId(closureId).action(action).reportVersion(1)
                .actorPersonId(persistPerson("Actor").getId()).occurredAt(at).build());
    }

    private void assign(SubjectSection section, Person person, InstructorRole role) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(role);
        entityManager.persist(assignment);
    }
}

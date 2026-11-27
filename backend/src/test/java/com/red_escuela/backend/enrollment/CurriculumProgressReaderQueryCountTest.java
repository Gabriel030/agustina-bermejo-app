package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.service.CurriculumProgressReader;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurriculumProgressReaderQueryCountTest extends AbstractIntegrationTest {
    private static final int FEW_SUBJECTS = 10;
    private static final int MANY_SUBJECTS = 100;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CurriculumProgressReader curriculumProgressReader;

    private int sequence = 71000000;
    private int planSequence = 0;

    @Test
    void queryCountDoesNotGrowWithPlanSubjectsAndPrerequisites() {
        Long fewId = chainedEnrollment(FEW_SUBJECTS).getId();
        Long manyId = chainedEnrollment(MANY_SUBJECTS).getId();
        long withFew = countQueries(() -> curriculumProgressReader.readProgress(fewId));
        long withMany = countQueries(() -> curriculumProgressReader.readProgress(manyId));

        assertThat(withMany).isEqualTo(withFew);
    }

    @Test
    void inactivePlanSubjectsAreExcludedFromTheCalculation() {
        ProgramEnrollment enrollment = persistEnrollment(3, 3);
        ProgramSubject inactive = plan(enrollment).get(2);
        inactive.setStatus(StatusType.INACTIVE);
        persistSubjectEnrollment(enrollment, plan(enrollment).get(0), EnrollmentStatus.APPROVED);
        flushAndClear();

        CurriculumProgressView view = curriculumProgressReader.readProgress(enrollment.getId());

        assertThat(view.subjects()).hasSize(2);
        assertThat(view.subjects()).extracting(CurriculumProgressView.SubjectView::subjectId)
                .doesNotContain(inactive.getSubject().getId());
        assertThat(view.summary().mandatoryTotal()).isEqualTo(2);
    }

    @Test
    void chainedPlanUnlocksSecondSubjectByApprovalAndThirdByWaiver() {
        ProgramEnrollment enrollment = chainedEnrollment(3);

        CurriculumProgressView view = curriculumProgressReader.readProgress(enrollment.getId());

        assertThat(view.subjects()).extracting(CurriculumProgressView.SubjectView::state)
                .containsExactly(CurriculumSubjectState.APPROVED, CurriculumSubjectState.AVAILABLE,
                        CurriculumSubjectState.AVAILABLE);
        assertThat(view.subjects().get(1).corequisites()).hasSize(1);
        assertThat(view.summary().electiveGroups()).hasSize(2);
    }

    private ProgramEnrollment chainedEnrollment(int subjects) {
        ProgramEnrollment enrollment = persistEnrollment(subjects, subjects);
        List<ProgramSubject> plan = plan(enrollment);
        for (int i = 1; i < plan.size(); i++) {
            ProgramSubjectPrerequisite prerequisite = new ProgramSubjectPrerequisite();
            prerequisite.setProgramSubject(plan.get(i));
            prerequisite.setPrerequisiteProgramSubject(plan.get(i - 1));
            prerequisite.setRequirementType(RequirementType.APPROVED);
            entityManager.persist(prerequisite);
            ProgramSubjectCorequisite corequisite = new ProgramSubjectCorequisite();
            corequisite.setProgramSubject(plan.get(i));
            corequisite.setCorequisiteProgramSubject(plan.get(i - 1));
            entityManager.persist(corequisite);
            persistElectiveGroup(enrollment, plan.get(i), i);
            if (i % 2 == 0) {
                persistWaiver(enrollment, plan.get(i));
            }
        }
        persistSubjectEnrollment(enrollment, plan.get(0), EnrollmentStatus.APPROVED);
        flushAndClear();
        return enrollment;
    }

    private void persistElectiveGroup(ProgramEnrollment enrollment, ProgramSubject programSubject, int index) {
        ElectiveGroup group = new ElectiveGroup();
        group.setName("Group " + programSubject.getId() + "-" + index);
        entityManager.persist(group);
        ProgramElectiveGroup programGroup = new ProgramElectiveGroup();
        programGroup.setProgram(enrollment.getProgram());
        programGroup.setElectiveGroup(group);
        programGroup.setMinRequired(1);
        programGroup.setMaxAllowed(2);
        entityManager.persist(programGroup);
        programSubject.setElectiveGroup(group);
    }

    private void persistWaiver(ProgramEnrollment enrollment, ProgramSubject programSubject) {
        DirectorApprovalRequest waiver = new DirectorApprovalRequest();
        waiver.setType(ExceptionType.PREREQUISITE_WAIVER);
        waiver.setProgramEnrollment(enrollment);
        waiver.setSubject(programSubject.getSubject());
        waiver.setJustification("Waiver");
        waiver.setStatus(DirectorApprovalRequestStatus.APPROVED);
        waiver.setRequestedBy(enrollment.getStudent().getPerson());
        waiver.setRequestedAt(LocalDateTime.now());
        entityManager.persist(waiver);
    }

    private long countQueries(Supplier<Object> action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            action.get();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<ProgramSubject> plan(ProgramEnrollment enrollment) {
        return entityManager.createQuery(
                        "select ps from ProgramSubject ps join fetch ps.subject where ps.program.id = :programId order by ps.id",
                        ProgramSubject.class)
                .setParameter("programId", enrollment.getProgram().getId())
                .getResultList();
    }

    private ProgramEnrollment persistEnrollment(int subjects, int requiredCredits) {
        Institution institution = new Institution();
        institution.setName("Perf institute");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = new Program();
        program.setName("Perf program");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(requiredCredits);
        entityManager.persist(program);
        for (int i = 0; i < subjects; i++) {
            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Subject " + (planSequence++) + "-" + i);
            entityManager.persist(subject);
            ProgramSubject programSubject = new ProgramSubject();
            programSubject.setProgram(program);
            programSubject.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
            programSubject.setSubject(subject);
            programSubject.setCredits(i == 0 ? 4 : 1);
            programSubject.setSortOrder(i);
            entityManager.persist(programSubject);
        }
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        entityManager.flush();
        return enrollment;
    }

    private void persistSubjectEnrollment(ProgramEnrollment enrollment, ProgramSubject programSubject, EnrollmentStatus status) {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-" + programSubject.getId());
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        SubjectSection section = new SubjectSection();
        section.setName("Section " + programSubject.getId());
        section.setSubject(programSubject.getSubject());
        section.setTerm(term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
        sectionEnrollment.setStudent(enrollment.getStudent());
        sectionEnrollment.setProgramEnrollment(enrollment);
        sectionEnrollment.setSubjectSection(section);
        sectionEnrollment.setStatus(status);
        sectionEnrollment.setEnrolledAt(LocalDateTime.now());
        sectionEnrollment.setAttemptNumber(1);
        entityManager.persist(sectionEnrollment);
    }

    private Person persistPerson() {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Name" + dni);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }
}

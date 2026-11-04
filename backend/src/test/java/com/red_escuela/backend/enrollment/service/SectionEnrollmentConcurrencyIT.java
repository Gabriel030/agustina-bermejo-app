package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SectionEnrollmentConcurrencyIT extends AbstractIntegrationTest {

    private static final List<EnrollmentStatus> SEAT_HOLDING = List.of(
            EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    @Autowired
    private SubjectSectionEnrollmentService service;
    @Autowired
    private SubjectSectionEnrollmentRepository enrollmentRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProgramEnrollmentRaceFixtures fixtures;
    private Program program;
    private Term term;
    private Subject subject;

    private void inTransaction(Runnable setup) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            fixtures = new ProgramEnrollmentRaceFixtures(entityManager);
            program = fixtures.cycle().program();
            term = fixtures.term();
            term.defineEnrollmentWindow(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(2100, 1, 1, 0, 0));
            subject = fixtures.cycle().subject();
            ProgramSubject programSubject = new ProgramSubject();
            programSubject.setProgram(program);
            programSubject.setSubject(subject);
            programSubject.setCredits(0);
            programSubject.setCurricularYear(1);
            programSubject.setCurricularTerm(CurricularTerm.FIRST);
            programSubject.setStatus(StatusType.ACTIVE);
            entityManager.persist(programSubject);
            setup.run();
            entityManager.flush();
        });
    }

    private SubjectSection section(int capacity) {
        return fixtures.section(subject, term, fixtures.cycleOf(program, term), capacity);
    }

    private SubjectSectionEnrollment enrollmentIn(SubjectSection section, EnrollmentStatus status, int minutes) {
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(program);
        return fixtures.sectionEnrollment(programEnrollment, section, status, LocalDateTime.now().plusMinutes(minutes));
    }

    private long seatsHeldIn(Long sectionId) {
        return enrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, SEAT_HOLDING).size();
    }

    private long waitlistedIn(Long sectionId) {
        return enrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, List.of(EnrollmentStatus.WAITLISTED)).size();
    }

    @RepeatedTest(5)
    void sameStudentEnrollingInTwoSectionsOfTheSameSubjectKeepsASingleActiveEnrollment() throws Exception {
        Long[] ids = new Long[3];
        inTransaction(() -> {
            SubjectSection first = section(20);
            SubjectSection second = section(20);
            ids[0] = fixtures.programEnrollment(program).getId();
            ids[1] = first.getId();
            ids[2] = second.getId();
        });
        Callable<Object> enrollInFirst = () -> service.enroll(ids[0], new CreateSectionEnrollmentRequest(ids[1]));
        Callable<Object> enrollInSecond = () -> service.enroll(ids[0], new CreateSectionEnrollmentRequest(ids[2]));

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(enrollInFirst, enrollInSecond));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .singleElement().satisfies(outcome -> assertThat(outcome.failure()).isInstanceOf(AlreadyEnrolledInSubjectException.class));
        assertThat(enrollmentRepository.findByProgramEnrollmentId(ids[0])).hasSize(1);
    }

    @RepeatedTest(5)
    void oppositeSectionChangesBetweenTheSameTwoSectionsBothComplete() throws Exception {
        Long[] ids = new Long[4];
        inTransaction(() -> {
            SubjectSection first = section(20);
            SubjectSection second = section(20);
            ids[0] = enrollmentIn(first, EnrollmentStatus.ENROLLED, 0).getId();
            ids[1] = enrollmentIn(second, EnrollmentStatus.ENROLLED, 1).getId();
            ids[2] = first.getId();
            ids[3] = second.getId();
        });
        Callable<Object> firstToSecond = () -> service.changeSection(ids[0], new ChangeSectionRequest(ids[3]));
        Callable<Object> secondToFirst = () -> service.changeSection(ids[1], new ChangeSectionRequest(ids[2]));

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(firstToSecond, secondToFirst));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(seatsHeldIn(ids[2])).isEqualTo(1);
        assertThat(seatsHeldIn(ids[3])).isEqualTo(1);
    }

    @RepeatedTest(5)
    void withdrawingAndChangingSectionOfTheSameEnrollmentSimultaneouslyPromotesOnlyOneWaitlistedStudent() throws Exception {
        Long[] ids = new Long[3];
        inTransaction(() -> {
            SubjectSection origin = section(1);
            SubjectSection target = section(20);
            ids[0] = enrollmentIn(origin, EnrollmentStatus.ENROLLED, 0).getId();
            enrollmentIn(origin, EnrollmentStatus.WAITLISTED, 1);
            enrollmentIn(origin, EnrollmentStatus.WAITLISTED, 2);
            ids[1] = origin.getId();
            ids[2] = target.getId();
        });
        Callable<Object> withdraw = () -> {
            service.withdraw(ids[0]);
            return null;
        };
        Callable<Object> change = () -> service.changeSection(ids[0], new ChangeSectionRequest(ids[2]));

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(withdraw, change));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(seatsHeldIn(ids[1])).isEqualTo(1);
        assertThat(waitlistedIn(ids[1])).isEqualTo(1);
    }

    @RepeatedTest(5)
    void withdrawingTheSameEnrollmentTwiceSimultaneouslyPromotesOnlyOneWaitlistedStudent() throws Exception {
        Long[] ids = new Long[2];
        inTransaction(() -> {
            SubjectSection section = section(1);
            ids[0] = enrollmentIn(section, EnrollmentStatus.ENROLLED, 0).getId();
            enrollmentIn(section, EnrollmentStatus.WAITLISTED, 1);
            enrollmentIn(section, EnrollmentStatus.WAITLISTED, 2);
            ids[1] = section.getId();
        });
        Callable<Object> firstWithdraw = () -> {
            service.withdraw(ids[0]);
            return null;
        };

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(firstWithdraw, firstWithdraw));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(seatsHeldIn(ids[1])).isEqualTo(1);
        assertThat(waitlistedIn(ids[1])).isEqualTo(1);
    }

    @RepeatedTest(5)
    void twoWithdrawalsFromTheSameSectionPromoteBothWaitlistedStudents() throws Exception {
        Long[] ids = new Long[3];
        inTransaction(() -> {
            SubjectSection section = section(2);
            ids[0] = enrollmentIn(section, EnrollmentStatus.ENROLLED, 0).getId();
            ids[1] = enrollmentIn(section, EnrollmentStatus.ENROLLED, 1).getId();
            enrollmentIn(section, EnrollmentStatus.WAITLISTED, 2);
            enrollmentIn(section, EnrollmentStatus.WAITLISTED, 3);
            ids[2] = section.getId();
        });
        Callable<Object> firstWithdraw = () -> {
            service.withdraw(ids[0]);
            return null;
        };
        Callable<Object> secondWithdraw = () -> {
            service.withdraw(ids[1]);
            return null;
        };

        List<Outcome<Object>> outcomes = ConcurrentRace.run(List.of(firstWithdraw, secondWithdraw));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(seatsHeldIn(ids[2])).isEqualTo(2);
        assertThat(waitlistedIn(ids[2])).isZero();
    }

    @Test
    void withdrawingFromAnOverbookedSectionDoesNotPromoteWaitlistedStudentsBeyondCapacity() {
        Long[] ids = new Long[2];
        inTransaction(() -> {
            SubjectSection section = section(1);
            ids[0] = enrollmentIn(section, EnrollmentStatus.ENROLLED, 0).getId();
            enrollmentIn(section, EnrollmentStatus.ENROLLED, 1);
            enrollmentIn(section, EnrollmentStatus.WAITLISTED, 2);
            ids[1] = section.getId();
        });

        service.withdraw(ids[0]);

        assertThat(seatsHeldIn(ids[1])).isEqualTo(1);
        assertThat(waitlistedIn(ids[1])).isEqualTo(1);
    }
}

package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionModificationStatus;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SectionModificationConcurrencyIT extends AbstractIntegrationTest {

    private static final int REPETITIONS = 5;
    private static final LocalTime START = LocalTime.of(10, 0);
    private static final LocalTime END = LocalTime.of(12, 0);

    @Autowired
    private RequestSectionModificationUseCase requestModification;
    @Autowired
    private ApproveSectionModificationUseCase approveModification;
    @Autowired
    private RejectSectionModificationUseCase rejectModification;
    @Autowired
    private SectionScheduleSlotRepository slotRepository;
    @Autowired
    private SectionModificationProposalRepository proposalRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long firstSectionId;
    private Long secondSectionId;
    private Long sharedClassroomId;
    private Long otherClassroomId;

    @BeforeEach
    void seedTwoSectionsOfTheSameTerm() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            Term term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
            AcademicOfferingCycle cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection first = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            SubjectSection second = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            fixtures.slot(first, fixtures.classroom(30), DayOfWeek.MONDAY, START, END);
            fixtures.slot(second, fixtures.classroom(30), DayOfWeek.MONDAY, START, END);
            sharedClassroomId = fixtures.classroom(30).getId();
            otherClassroomId = fixtures.classroom(30).getId();
            firstSectionId = first.getId();
            secondSectionId = second.getId();
        });
    }

    private ModifySectionRequest tuesdayInSharedClassroom() {
        return new ModifySectionRequest(List.of(new ScheduleSlotRequest(sharedClassroomId, DayOfWeek.TUESDAY, START, END)));
    }

    private List<Throwable> runSimultaneously(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.call();
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            Throwable failure = future.get(30, TimeUnit.SECONDS);
            if (failure != null) {
                failures.add(failure);
            }
        }
        pool.shutdownNow();
        return failures;
    }

    private List<SectionScheduleSlot> slotsInSharedClassroom() {
        return new TransactionTemplate(transactionManager).execute(status ->
                slotRepository.findAll().stream()
                        .filter(slot -> slot.getClassroom().getId().equals(sharedClassroomId))
                        .toList());
    }

    private List<SectionModificationProposal> proposalsOf(Long sectionId, SectionModificationStatus status) {
        return new TransactionTemplate(transactionManager).execute(txStatus ->
                proposalRepository.findAll().stream()
                        .filter(proposal -> proposal.getSubjectSection().getId().equals(sectionId))
                        .filter(proposal -> proposal.getStatus() == status)
                        .toList());
    }

    @RepeatedTest(REPETITIONS)
    void approvingTheSamePendingModificationFromTwoThreadsAppliesItOnlyOnce() throws Exception {
        requestModification.requestModification(firstSectionId, tuesdayInSharedClassroom());

        List<Throwable> failures = runSimultaneously(List.of(
                () -> {
                    approveModification.approveModification(firstSectionId);
                    return null;
                },
                () -> {
                    approveModification.approveModification(firstSectionId);
                    return null;
                }));

        assertThat(failures).singleElement().isInstanceOfAny(ConflictException.class, ObjectOptimisticLockingFailureException.class);
        assertThat(slotsInSharedClassroom()).hasSize(1);
        assertThat(proposalsOf(firstSectionId, SectionModificationStatus.APPROVED)).hasSize(1);
    }

    @RepeatedTest(REPETITIONS)
    void approvingAndRejectingTheSamePendingModificationFromTwoThreadsLeavesASingleOutcome() throws Exception {
        requestModification.requestModification(firstSectionId, tuesdayInSharedClassroom());

        List<Throwable> failures = runSimultaneously(List.of(
                () -> {
                    approveModification.approveModification(firstSectionId);
                    return null;
                },
                () -> {
                    rejectModification.rejectModification(firstSectionId, "classroom reserved for exams");
                    return null;
                }));

        assertThat(failures).singleElement().isInstanceOfAny(ConflictException.class, ObjectOptimisticLockingFailureException.class);
        int approved = proposalsOf(firstSectionId, SectionModificationStatus.APPROVED).size();
        int rejected = proposalsOf(firstSectionId, SectionModificationStatus.REJECTED).size();
        assertThat(approved + rejected).isEqualTo(1);
        assertThat(slotsInSharedClassroom()).hasSize(approved);
    }

    @RepeatedTest(REPETITIONS)
    void approvingTwoModificationsThatTakeTheSameClassroomSlotFromTwoThreadsNeverDoubleBooksIt() throws Exception {
        requestModification.requestModification(firstSectionId, tuesdayInSharedClassroom());
        requestModification.requestModification(secondSectionId, tuesdayInSharedClassroom());

        List<Throwable> failures = runSimultaneously(List.of(
                () -> {
                    approveModification.approveModification(firstSectionId);
                    return null;
                },
                () -> {
                    approveModification.approveModification(secondSectionId);
                    return null;
                }));

        assertThat(failures).singleElement().isInstanceOf(SectionScheduleConflictException.class);
        assertThat(slotsInSharedClassroom()).hasSize(1);
    }

    @RepeatedTest(REPETITIONS)
    void approvingModificationsThatTakeTheSameTwoClassroomsInOppositeOrderNeverDeadlocks() throws Exception {
        requestModification.requestModification(firstSectionId, new ModifySectionRequest(List.of(
                new ScheduleSlotRequest(sharedClassroomId, DayOfWeek.TUESDAY, START, END),
                new ScheduleSlotRequest(otherClassroomId, DayOfWeek.WEDNESDAY, START, END))));
        requestModification.requestModification(secondSectionId, new ModifySectionRequest(List.of(
                new ScheduleSlotRequest(otherClassroomId, DayOfWeek.THURSDAY, START, END),
                new ScheduleSlotRequest(sharedClassroomId, DayOfWeek.FRIDAY, START, END))));

        List<Throwable> failures = runSimultaneously(List.of(
                () -> {
                    approveModification.approveModification(firstSectionId);
                    return null;
                },
                () -> {
                    approveModification.approveModification(secondSectionId);
                    return null;
                }));

        assertThat(failures).isEmpty();
        assertThat(slotsInSharedClassroom()).hasSize(2);
    }
}

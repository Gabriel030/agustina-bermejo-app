package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class CreateCycleSectionConcurrencyIT extends AbstractIntegrationTest {

    private static final int REPETITIONS = 10;
    private static final LocalTime START = LocalTime.of(10, 0);
    private static final LocalTime END = LocalTime.of(12, 0);

    @Autowired
    private CreateCycleSectionUseCase createSection;
    @Autowired
    private SectionScheduleSlotRepository slotRepository;
    @Autowired
    private SubjectSectionRepository subjectSectionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long cycleId;
    private Long subjectId;
    private Long sharedClassroomId;

    @BeforeEach
    void seedAnEditableCycleWithASubjectAndAClassroom() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            var term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
            cycleId = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT).getId();
            subjectId = fixtures.subject().getId();
            sharedClassroomId = fixtures.classroom(30).getId();
        });
    }

    private CreateCycleSectionRequest sectionInSharedClassroom(String name) {
        return new CreateCycleSectionRequest(name, subjectId, null, 20,
                List.of(new ScheduleSlotRequest(sharedClassroomId, DayOfWeek.MONDAY, START, END)), null, SectionShift.MORNING, null);
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

    @RepeatedTest(REPETITIONS)
    void creatingTwoSectionsThatTakeTheSameClassroomSlotFromTwoThreadsNeverDoubleBooksIt() throws Exception {
        List<Throwable> failures = runSimultaneously(List.of(
                () -> {
                    createSection.createSection(cycleId, sectionInSharedClassroom("Comision A"));
                    return null;
                },
                () -> {
                    createSection.createSection(cycleId, sectionInSharedClassroom("Comision B"));
                    return null;
                }));

        assertThat(failures).singleElement().isInstanceOf(SectionScheduleConflictException.class);
        assertThat(slotsInSharedClassroom()).hasSize(1);
    }

    private CreateCycleSectionRequest sectionWithoutSlots(String name) {
        return new CreateCycleSectionRequest(name, subjectId, null, 20, null, null, SectionShift.MORNING, null);
    }

    @RepeatedTest(REPETITIONS)
    void creatingTwoSectionsOfTheSameSubjectAndTermFromTwoThreadsNeverRepeatsTheCode() throws Exception {
        List<Throwable> failures = runSimultaneously(List.of(
                () -> {
                    createSection.createSection(cycleId, sectionWithoutSlots("Comision A"));
                    return null;
                },
                () -> {
                    createSection.createSection(cycleId, sectionWithoutSlots("Comision B"));
                    return null;
                }));

        var codes = new TransactionTemplate(transactionManager).execute(status ->
                subjectSectionRepository.findAll().stream()
                        .filter(section -> section.getSubject().getId().equals(subjectId))
                        .map(section -> section.getCode())
                        .sorted()
                        .toList());
        assertThat(failures).isEmpty();
        assertThat(codes).hasSize(2).doesNotHaveDuplicates();
        assertThat(codes.get(0)).endsWith("-01");
        assertThat(codes.get(1)).endsWith("-02");
    }
}

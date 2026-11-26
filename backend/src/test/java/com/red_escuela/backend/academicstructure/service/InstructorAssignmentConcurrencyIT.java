package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.exception.InstructorScheduleConflictException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
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
class InstructorAssignmentConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private SubjectSectionService subjectSectionService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long instructorId;
    private Long firstSectionId;
    private Long secondSectionId;

    @BeforeEach
    void seedTwoOverlappingSectionsInDifferentClassrooms() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            var term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
            var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            instructorId = fixtures.instructor().getId();
            var first = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            fixtures.slot(first, fixtures.classroom(30), DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(21, 0));
            var second = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            fixtures.slot(second, fixtures.classroom(30), DayOfWeek.MONDAY, LocalTime.of(20, 0), LocalTime.of(22, 0));
            firstSectionId = first.getId();
            secondSectionId = second.getId();
        });
    }

    private Callable<Void> assignTo(Long sectionId) {
        return () -> {
            subjectSectionService.assignInstructor(sectionId,
                    new AssignInstructorRequest(instructorId, InstructorRole.LEAD, null, null, null, null));
            return null;
        };
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

    @RepeatedTest(10)
    void assigningTheSameInstructorToTwoOverlappingSectionsFromTwoThreadsReportsAScheduleConflict() throws Exception {
        List<Throwable> failures = runSimultaneously(List.of(assignTo(firstSectionId), assignTo(secondSectionId)));

        assertThat(failures).singleElement().isInstanceOf(InstructorScheduleConflictException.class);
    }
}

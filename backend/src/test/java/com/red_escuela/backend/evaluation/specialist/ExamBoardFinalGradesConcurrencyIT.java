package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradeEntryRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.exception.ExamBoardFinalGradeRowRejectedException;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@SlowTest(reason = "Runs the batch load against the withdrawal and the close of the same board from concurrent threads on Postgres in several rounds")
class ExamBoardFinalGradesConcurrencyIT extends ExamBoardChangeFixture {

    private static final AtomicInteger DNI_SEQUENCE = new AtomicInteger(75000000);

    @Autowired
    private ExamBoardFinalGradesRecorder finalGradesRecorder;

    @Autowired
    private ExamBoardRegistrationManager registrationManager;

    @Autowired
    private ExamBoardManager examBoardManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private record Fixture(Long boardId, Long instructorId, List<Long> registrationIds, List<Long> enrollmentIds) {
    }

    private String nextDni() {
        return String.valueOf(DNI_SEQUENCE.incrementAndGet());
    }

    private Fixture fixture(int students) {
        return transactionTemplate.execute(status -> {
            Subject subject = subject("Materia concurrente " + nextDni(), null);
            Term term = term();
            ExamBoard board = examBoard(subject, term, LocalDateTime.now().minusDays(1));
            Instructor tribunal = tribunalMember(board, nextDni());
            List<Long> registrations = new java.util.ArrayList<>();
            List<Long> enrollments = new java.util.ArrayList<>();
            for (int index = 0; index < students; index++) {
                String dni = nextDni();
                SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, dni, "Carrera " + dni);
                EvaluationScheme scheme = new EvaluationScheme();
                scheme.setName("Esquema " + dni);
                scheme.setExamCountExpected(0);
                scheme.setTpCountExpected(0);
                scheme.setHasFinal(true);
                scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
                scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
                scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
                entityManager.persist(scheme);
                enrollment.getSubjectSection().setEvaluationScheme(scheme);
                ExamBoardRegistration registration = registration(board, enrollment);
                entityManager.flush();
                registrations.add(registration.getId());
                enrollments.add(enrollment.getId());
            }
            return new Fixture(board.getId(), tribunal.getId(), registrations, enrollments);
        });
    }

    private ExamBoardFinalGradeEntryRequest score(Long registrationId, String score) {
        return new ExamBoardFinalGradeEntryRequest(registrationId, Grade.of(new BigDecimal(score)), null, null);
    }

    private long gradeItemsOfBoard(Long boardId) {
        return jdbc.queryForObject("select count(*) from grade_item where exam_board_id = ?", Long.class, boardId);
    }

    private Throwable runTogether(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> firstResult = executor.submit(() -> attempt(first, start));
            Future<Throwable> secondResult = executor.submit(() -> attempt(second, start));
            start.countDown();
            Throwable secondFailure = secondResult.get();
            assertThat(secondFailure).as("the competing operation").isNull();
            return firstResult.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable attempt(Callable<Object> task, CountDownLatch start) {
        try {
            start.await();
            task.call();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @Test
    void aFailingRowAnywhereInTheBatchSavesNoGradeAndChangesNoRegistration() {
        Fixture fixture = fixture(3);
        jdbc.update("update exam_board_registration set status = 'WITHDRAWN' where id = ?", fixture.registrationIds().get(2));

        assertThatThrownBy(() -> finalGradesRecorder.record(fixture.boardId(), fixture.instructorId(), List.of(
                score(fixture.registrationIds().get(0), "7"),
                score(fixture.registrationIds().get(1), "8"),
                score(fixture.registrationIds().get(2), "9"))))
                .isInstanceOf(ExamBoardFinalGradeRowRejectedException.class)
                .satisfies(failure -> assertThat(((ExamBoardFinalGradeRowRejectedException) failure).details())
                        .containsEntry("registrationId", fixture.registrationIds().get(2)));

        assertThat(gradeItemsOfBoard(fixture.boardId())).isZero();
        assertThat(jdbc.queryForList("select status from exam_board_registration where exam_board_id = ? order by id",
                String.class, fixture.boardId())).containsExactly("REGISTERED", "REGISTERED", "WITHDRAWN");
    }

    @Test
    void theBatchAndTheWithdrawalOfTheSameRegistrationEndInOneOfTheTwoSerialOutcomes() throws Exception {
        for (int round = 0; round < 3; round++) {
            Fixture fixture = fixture(1);
            Long registrationId = fixture.registrationIds().get(0);

            Throwable batchFailure = runTogether(
                    () -> finalGradesRecorder.record(fixture.boardId(), fixture.instructorId(), List.of(score(registrationId, "7"))),
                    () -> {
                        registrationManager.withdraw(registrationId);
                        return null;
                    });

            String status = jdbc.queryForObject("select status from exam_board_registration where id = ?", String.class, registrationId);
            if (batchFailure == null) {
                assertThat(gradeItemsOfBoard(fixture.boardId())).as("round " + round).isEqualTo(1L);
            } else {
                assertThat(batchFailure).as("round " + round).isInstanceOf(ExamBoardFinalGradeRowRejectedException.class);
                assertThat(gradeItemsOfBoard(fixture.boardId())).isZero();
                assertThat(status).isEqualTo("WITHDRAWN");
            }
        }
    }

    @Test
    void theBatchAndTheCloseOfTheSameBoardNeverLeaveAGradeOnAnAbsentRegistration() throws Exception {
        for (int round = 0; round < 3; round++) {
            Fixture fixture = fixture(2);

            Throwable batchFailure = runTogether(
                    () -> finalGradesRecorder.record(fixture.boardId(), fixture.instructorId(), List.of(
                            score(fixture.registrationIds().get(0), "7"), score(fixture.registrationIds().get(1), "8"))),
                    () -> examBoardManager.close(fixture.boardId()));

            List<String> statuses = jdbc.queryForList("select status from exam_board_registration where exam_board_id = ? order by id",
                    String.class, fixture.boardId());
            if (batchFailure == null) {
                assertThat(gradeItemsOfBoard(fixture.boardId())).as("round " + round).isEqualTo(2L);
                assertThat(statuses).containsExactly("GRADED", "GRADED");
            } else {
                assertThat(gradeItemsOfBoard(fixture.boardId())).as("round " + round).isZero();
                assertThat(statuses).containsExactly("ABSENT", "ABSENT");
            }
        }
    }
}

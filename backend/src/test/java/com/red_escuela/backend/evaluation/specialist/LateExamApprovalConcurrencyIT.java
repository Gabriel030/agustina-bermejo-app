package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.api.LateExamApprovalChecker;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@SlowTest(reason = "Consumes one late exam approval from two concurrent transactions on Postgres")
class LateExamApprovalConcurrencyIT extends ExamBoardChangeFixture {

    private static final AtomicInteger DNI_SEQUENCE = new AtomicInteger(78000000);

    @Autowired
    private LateExamApprovalChecker checker;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private record Approval(Long programEnrollmentId, Long subjectId, Long requestId) {
    }

    private Approval approval() {
        return transactionTemplate.execute(status -> {
            String dni = String.valueOf(DNI_SEQUENCE.incrementAndGet());
            Subject subject = subject("Materia tardia " + dni, null);
            Term term = term();
            SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, dni, "Carrera " + dni);
            DirectorApprovalRequest request = DirectorApprovalRequest.open(ExceptionType.LATE_EXAM,
                    enrollment.getProgramEnrollment(), subject, "Motivo", person(dni + "1"), LocalDateTime.of(2026, 1, 1, 10, 0));
            request.decide(DirectorApprovalRequestStatus.APPROVED, person(dni + "2"), "Ok", LocalDateTime.of(2026, 1, 2, 10, 0));
            entityManager.persist(request);
            entityManager.flush();
            return new Approval(enrollment.getProgramEnrollment().getId(), subject.getId(), request.getId());
        });
    }

    private Boolean consume(Approval approval) {
        return transactionTemplate.execute(status -> checker.consumeApprovedLateExam(approval.programEnrollmentId(), approval.subjectId()));
    }

    @Test
    void exactlyOneOfTwoConcurrentConsumersWinsTheSameApproval() throws Exception {
        Approval approval = approval();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> {
                start.await();
                return consume(approval);
            };
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select consumed_at from director_approval_request where id = ?",
                java.sql.Timestamp.class, approval.requestId())).isNotNull();
    }

    @Test
    void anApprovalAlreadyConsumedCannotBeConsumedAgain() {
        Approval approval = approval();

        assertThat(consume(approval)).isTrue();
        assertThat(consume(approval)).isFalse();
    }
}

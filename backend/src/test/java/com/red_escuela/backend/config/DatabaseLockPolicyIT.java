package com.red_escuela.backend.config;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseLockPolicyIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Environment environment;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private CourseCatalogFinder courseCatalogFinder;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long persistCourse() {
        var term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.now().plusMonths(1));
        Course course = new Course();
        course.setName("Curso de politica de locks " + System.nanoTime());
        course.setTerm(termRepository.save(term));
        course.setCapacity(1);
        course.setIsActive(StatusType.ACTIVE);
        return courseRepository.save(course).getId();
    }

    @Test
    void pooledConnectionsStartWithTheConfiguredLockAndStatementTimeouts() {
        assertThat(jdbcTemplate.queryForObject("show lock_timeout", String.class)).isEqualTo("10s");
        assertThat(jdbcTemplate.queryForObject("show statement_timeout", String.class)).isEqualTo("1min");
    }

    @Test
    void flywayMigrationsRunWithoutStatementTimeout() {
        assertThat(environment.getProperty("spring.flyway.init-sqls")).isEqualTo("SET statement_timeout = 0");
    }

    @Test
    void lockWaitTimeoutSurfacesAsAnExceptionTheAdviceMapsToARetryableConflict() throws Exception {
        Long courseId = persistCourse();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> holderTransaction = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                courseCatalogFinder.lockCourse(courseId);
                holding.countDown();
                awaitQuietly(release);
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            Throwable failure = catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.execute("set local lock_timeout = '300ms'");
                courseCatalogFinder.lockCourse(courseId);
            }));
            release.countDown();
            holderTransaction.get(10, TimeUnit.SECONDS);

            assertThat(failure).isInstanceOf(PessimisticLockingFailureException.class);
            ErrorCodeResponse response = new GlobalExceptionHandler()
                    .handlePessimisticLockingFailure((PessimisticLockingFailureException) failure);
            assertThat(response.code()).isEqualTo(ErrorCodeResponse.LOCK_CONFLICT_CODE);
            assertThat(response.retryable()).isTrue();
            assertThat(response.toString()).doesNotContain("lock_timeout", "relation", "courses");
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
    }

    @Test
    void statementTimeoutSurfacesAsAnExceptionTheAdviceMapsToServiceUnavailable() {
        Throwable failure = catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.execute("set local statement_timeout = '200ms'");
            jdbcTemplate.queryForObject("select pg_sleep(5)::text", String.class);
        }));

        assertThat(failure).isInstanceOf(QueryTimeoutException.class);
        ResponseEntity<ErrorCodeResponse> response = new GlobalExceptionHandler().handleQueryTimeout((QueryTimeoutException) failure);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodeResponse.DATABASE_BUSY_CODE);
        assertThat(response.getBody().retryable()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

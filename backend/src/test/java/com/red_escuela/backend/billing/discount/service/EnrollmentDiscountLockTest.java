package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("D-238")
@Tag("D-284")
class EnrollmentDiscountLockTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EnrollmentDiscountLock lock = new EnrollmentDiscountLock(jdbc);

    @Test
    void aProgramEnrollmentLocksItsRowBeforeTakingTheAdvisoryLockOfEvenKey() {
        lock.lockDiscountsOf(EnrollmentReference.ofProgram(21L));

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForList("select id from program_enrollment where id = ? for update", Long.class, 21L);
        order.verify(jdbc).queryForObject("select pg_advisory_xact_lock(?)::text", String.class, 42L);
    }

    @Test
    void aCourseEnrollmentLocksItsRowBeforeTakingTheAdvisoryLockOfOddKey() {
        lock.lockDiscountsOf(EnrollmentReference.ofCourse(21L));

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForList("select id from course_enrollment where id = ? for update", Long.class, 21L);
        order.verify(jdbc).queryForObject("select pg_advisory_xact_lock(?)::text", String.class, 43L);
        verify(jdbc, never()).queryForList(eq("select id from program_enrollment where id = ? for update"), eq(Long.class), any(Object.class));
    }
}

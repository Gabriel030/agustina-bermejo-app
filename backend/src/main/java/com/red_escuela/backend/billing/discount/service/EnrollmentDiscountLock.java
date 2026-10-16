package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentDiscountLock {
    private static final String LOCK_PROGRAM_ENROLLMENT_ROW = "select id from program_enrollment where id = ? for update";
    private static final String LOCK_COURSE_ENROLLMENT_ROW = "select id from course_enrollment where id = ? for update";

    private final JdbcTemplate jdbc;

    public EnrollmentDiscountLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockDiscountsOf(EnrollmentReference enrollment) {
        jdbc.queryForList(enrollment.kind() == EnrollmentKind.PROGRAM ? LOCK_PROGRAM_ENROLLMENT_ROW : LOCK_COURSE_ENROLLMENT_ROW, Long.class, enrollment.id());
        long key = enrollment.id() * 2 + (enrollment.kind() == EnrollmentKind.PROGRAM ? 0 : 1);
        jdbc.queryForObject("select pg_advisory_xact_lock(?)::text", String.class, key);
    }
}

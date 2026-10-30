package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PaymentStandingSeed {
    private static final LocalDate DEFAULT_SUSPENDED_SINCE = LocalDate.of(2027, 4, 1);

    private PaymentStandingSeed() {
    }

    public static void seedProgramEnrollmentStanding(JdbcTemplate jdbc, Long programEnrollmentId, EnrollmentPaymentStatus status) {
        seedProgramEnrollmentStanding(jdbc, programEnrollmentId, status, DEFAULT_SUSPENDED_SINCE, LocalDateTime.of(2027, 4, 1, 8, 0));
    }

    public static void seedProgramEnrollmentStanding(JdbcTemplate jdbc, Long programEnrollmentId, EnrollmentPaymentStatus status, LocalDate suspendedSince,
                                                     LocalDateTime changedAt) {
        Long standingId = jdbc.queryForObject("""
                insert into enrollment_payment_standing (program_enrollment_id, student_id, status, suspended_since, last_closed_month)
                select id, student_id, cast(? as varchar), cast(? as date), date '2020-01-01' from program_enrollment where id = ?
                on conflict (program_enrollment_id) where program_enrollment_id is not null
                do update set status = excluded.status, suspended_since = excluded.suspended_since
                returning id
                """, Long.class, status.name(), suspendedSinceOf(status, suspendedSince), programEnrollmentId);
        recordChange(jdbc, standingId, status, changedAt);
    }

    public static void seedCourseEnrollmentStanding(JdbcTemplate jdbc, Long courseEnrollmentId, EnrollmentPaymentStatus status) {
        Long standingId = jdbc.queryForObject("""
                insert into enrollment_payment_standing (course_enrollment_id, student_id, status, suspended_since, last_closed_month)
                select id, student_id, cast(? as varchar), cast(? as date), date '2020-01-01' from course_enrollment where id = ?
                on conflict (course_enrollment_id) where course_enrollment_id is not null
                do update set status = excluded.status, suspended_since = excluded.suspended_since
                returning id
                """, Long.class, status.name(), suspendedSinceOf(status, DEFAULT_SUSPENDED_SINCE), courseEnrollmentId);
        recordChange(jdbc, standingId, status, LocalDateTime.of(2027, 4, 1, 8, 0));
    }

    public static void seedProgramEnrollmentStanding(jakarta.persistence.EntityManager entityManager, Long programEnrollmentId, EnrollmentPaymentStatus status) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                insert into enrollment_payment_standing (program_enrollment_id, student_id, status, suspended_since, last_closed_month)
                select id, student_id, cast(:status as varchar), cast(:since as date), date '2020-01-01' from program_enrollment where id = :id
                """).setParameter("status", status.name()).setParameter("since", suspendedSinceOf(status, DEFAULT_SUSPENDED_SINCE)).setParameter("id", programEnrollmentId).executeUpdate();
    }

    public static void seedCourseEnrollmentStanding(jakarta.persistence.EntityManager entityManager, Long courseEnrollmentId, EnrollmentPaymentStatus status) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                insert into enrollment_payment_standing (course_enrollment_id, student_id, status, suspended_since, last_closed_month)
                select id, student_id, cast(:status as varchar), cast(:since as date), date '2020-01-01' from course_enrollment where id = :id
                """).setParameter("status", status.name()).setParameter("since", suspendedSinceOf(status, DEFAULT_SUSPENDED_SINCE)).setParameter("id", courseEnrollmentId).executeUpdate();
    }

    private static java.sql.Date suspendedSinceOf(EnrollmentPaymentStatus status, LocalDate suspendedSince) {
        return status == EnrollmentPaymentStatus.SUSPENDED ? java.sql.Date.valueOf(suspendedSince) : null;
    }

    private static void recordChange(JdbcTemplate jdbc, Long standingId, EnrollmentPaymentStatus status, LocalDateTime changedAt) {
        jdbc.update("""
                insert into enrollment_payment_status_change (standing_id, event, from_status, to_status, actor_process, business_date, occurred_at)
                values (?, 'TEST_SEED', null, ?, 'TEST', ?, ?)
                """, standingId, status.name(), java.sql.Date.valueOf(changedAt.toLocalDate()), java.sql.Timestamp.valueOf(changedAt));
    }
}

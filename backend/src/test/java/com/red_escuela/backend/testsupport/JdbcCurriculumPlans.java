package com.red_escuela.backend.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCurriculumPlans {

    private JdbcCurriculumPlans() {
    }

    public static Long planIdOf(JdbcTemplate jdbc, Long programId) {
        return jdbc.queryForList("select id from curriculum_plan where program_id = ? and status <> 'DRAFT' order by version_number desc limit 1",
                Long.class, programId).stream().findFirst().orElseGet(() -> jdbc.queryForObject(
                "insert into curriculum_plan (program_id, version_number, name, status, valid_from, total_credits_required) "
                        + "values (?, 1, 'Fixture plan', 'ACTIVE', date '2020-01-01', 0) returning id", Long.class, programId));
    }

    public static Long openCohortIdOf(JdbcTemplate jdbc, Long programId) {
        return jdbc.queryForList("select id from program_cohort where program_id = ? order by id limit 1", Long.class, programId).stream().findFirst()
                .orElseGet(() -> {
                    Long termId = jdbc.queryForList("select id from term where status = 'ACTIVE' and year_number = extract(year from current_date) order by id limit 1",
                            Long.class).stream().findFirst().orElseGet(() -> jdbc.queryForObject(
                            "insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) "
                                    + "values (extract(year from current_date), 'ACTIVE', now() - interval '60 days', now() + interval '300 days', "
                                    + "timestamp '2000-01-01 00:00:00', timestamp '2999-12-31 00:00:00') returning id", Long.class));
                    return jdbc.queryForObject("insert into program_cohort (program_id, term_id, plan_id, intake, admission_start, admission_end, cursada_start, "
                            + "created_by_id, created_at, opened_at) values (?, ?, ?, 'FIRST', current_date - 30, current_date + 30, current_date, "
                            + "(select min(id) from person), now(), now()) returning id", Long.class, programId, termId, planIdOf(jdbc, programId));
                });
    }

    public static Long enrollmentOf(JdbcTemplate jdbc, Long studentId, Long programId) {
        return jdbc.queryForObject("insert into program_enrollment (student_id, program_id, plan_id, status, enrollment_date) values (?, ?, ?, 'ENROLLED', now()) returning id",
                Long.class, studentId, programId, planIdOf(jdbc, programId));
    }
}

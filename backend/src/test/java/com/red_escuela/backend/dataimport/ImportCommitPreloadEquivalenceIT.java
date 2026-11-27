package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class ImportCommitPreloadEquivalenceIT extends AbstractDataImportIT {
    private static final String PROGRAM_ENROLLMENTS_OF_BATCH = """
            select p.dni || ' ' || e.status || ' ' || e.enrollment_date::date || ' completion=' || coalesce(e.completion_date::date::text, '-')
                || ' withdrawal=' || coalesce(e.withdrawal_effective_date::text, '-') || ' entryYear=' || coalesce(e.entry_year::text, '-')
                || ' cohort=' || coalesce(c.intake, '-') || ' cohortStart=' || coalesce(c.cursada_start::text, '-')
            from program_enrollment e
            join student s on s.id = e.student_id
            join person p on p.id = s.person_id
            left join program_cohort c on c.id = e.program_cohort_id
            where e.id in (select entity_id from import_created_entity where batch_id = ? and entity_type = 'PROGRAM_ENROLLMENTS')
            order by p.dni
            """;
    private static final String SECTION_ENROLLMENTS_OF_BATCH = """
            select p.dni || ' ' || ss.code || ' attempt=' || e.attempt_number || ' ' || e.status
                || ' score=' || coalesce(e.final_score::text, '-') || ' type=' || coalesce(e.final_grade_type, '-')
                || ' finalDate=' || coalesce(e.final_score_date::text, '-') || ' enrolledAt=' || e.enrolled_at::date
                || ' programEnrollmentStatus=' || pe.status
                || ' closure=' || coalesce((select cs.final_condition || '/' || coalesce(cs.promotion_grade::text, '-')
                    from commission_closure_student cs where cs.enrollment_id = e.id), 'none')
            from subject_section_enrollment e
            join student s on s.id = e.student_id
            join person p on p.id = s.person_id
            join subject_section ss on ss.id = e.program_section_id
            join program_enrollment pe on pe.id = e.program_enrollment_id
            where e.id in (select entity_id from import_created_entity where batch_id = ? and entity_type = 'SECTION_ENROLLMENTS')
            order by p.dni, ss.code, e.attempt_number
            """;

    @Test
    void committingProgramAndSectionEnrollmentsCreatesTheSameEntitiesTrackingAndRollback() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZC", year, firstDni, 6, 4);
        String program = csv.program();

        long structureBatch = createBatch();
        long testBatch = createBatch();
        try {
            importBaseStructure(structureBatch, csv, HistoricalDatasetCsv.PROGRAM_ENROLLMENT_HEADER, csv.terms());
            upload(testBatch, "TERMS", HistoricalDatasetCsv.TERM_HEADER
                    + year + ",ANNUAL,Año lectivo," + year + "-03-01,15/12/" + year + "," + program + "\n");
            upload(testBatch, "PROGRAM_ENROLLMENTS", HistoricalDatasetCsv.PROGRAM_ENROLLMENT_HEADER
                    + "DNI," + csv.studentDni(0) + "," + program + "," + year + "-03-01,ENROLLED,,," + year + ",FIRST\n"
                    + "DNI," + csv.studentDni(1) + "," + program + "," + year + "-03-02,ENROLLED,,," + year + ",FIRST\n"
                    + "DNI," + csv.studentDni(2) + "," + program + "," + year + "-03-03,COMPLETED," + year + "-12-15,," + year + ",FIRST\n"
                    + "DNI," + csv.studentDni(3) + "," + program + "," + year + "-03-04,WITHDRAWN," + year + "-09-01," + year + "-08-30," + year + ",FIRST\n"
                    + "DNI," + csv.studentDni(4) + "," + program + "," + year + "-03-05,ENROLLED,,," + year + ",SECOND\n"
                    + "DNI," + csv.studentDni(5) + "," + program + "," + year + "-03-06,WITHDRAWN," + year + "-09-10,," + year + ",FIRST\n");
            upload(testBatch, "SECTION_ENROLLMENTS", HistoricalDatasetCsv.SECTION_ENROLLMENT_HEADER
                    + row(csv.studentDni(0), program, csv.sectionCode(0), 1, "APPROVED", "\"8,50\"", "PROMOTION", year + "-07-20")
                    + row(csv.studentDni(0), program, csv.sectionCode(1), 1, "APPROVED", "7", "FINAL_EXAM", year + "-07-21")
                    + row(csv.studentDni(1), program, csv.sectionCode(0), 1, "REGULAR", "", "", "")
                    + row(csv.studentDni(1), program, csv.sectionCode(1), 1, "FAILED", "3", "FINAL_EXAM", year + "-07-22")
                    + row(csv.studentDni(2), program, csv.sectionCode(2), 1, "WITHDRAWN", "", "", "")
                    + row(csv.studentDni(3), program, csv.sectionCode(3), 2, "APPROVED", "9", "PROMOTION", year + "-07-23")
                    + row(csv.studentDni(4), program, csv.sectionCode(0), 1, "APPROVED", "10", "PROMOTION", year + "-07-24"));

            validate(testBatch).andExpect(jsonPath("$.status").value("VALIDATED"));
            commit(testBatch).andExpect(jsonPath("$.status").value("COMMITTED"));

            String start = year + "-03-15";
            assertThat(jdbc.queryForList(PROGRAM_ENROLLMENTS_OF_BATCH, String.class, testBatch)).containsExactly(
                    csv.studentDni(0) + " ENROLLED " + year + "-03-01 completion=- withdrawal=- entryYear=" + year + " cohort=FIRST cohortStart=" + year + "-03-01",
                    csv.studentDni(1) + " ENROLLED " + year + "-03-02 completion=- withdrawal=- entryYear=" + year + " cohort=FIRST cohortStart=" + year + "-03-01",
                    csv.studentDni(2) + " COMPLETED " + year + "-03-03 completion=" + year + "-12-15 withdrawal=- entryYear=" + year + " cohort=FIRST cohortStart=" + year + "-03-01",
                    csv.studentDni(3) + " WITHDRAWN " + year + "-03-04 completion=" + year + "-09-01 withdrawal=" + year + "-08-30 entryYear=" + year + " cohort=FIRST cohortStart=" + year + "-03-01",
                    csv.studentDni(4) + " ENROLLED " + year + "-03-05 completion=- withdrawal=- entryYear=" + year + " cohort=SECOND cohortStart=" + year + "-08-01",
                    csv.studentDni(5) + " WITHDRAWN " + year + "-03-06 completion=" + year + "-09-10 withdrawal=" + year + "-09-10 entryYear=" + year + " cohort=FIRST cohortStart=" + year + "-03-01");
            assertThat(jdbc.queryForList(SECTION_ENROLLMENTS_OF_BATCH, String.class, testBatch)).containsExactly(
                    csv.studentDni(0) + " " + csv.sectionCode(0) + " attempt=1 APPROVED score=8.50 type=PROMOTION finalDate=" + year
                            + "-07-20 enrolledAt=" + start + " programEnrollmentStatus=ENROLLED closure=PROMOTES/8.50",
                    csv.studentDni(0) + " " + csv.sectionCode(1) + " attempt=1 APPROVED score=7.00 type=FINAL_EXAM finalDate=" + year
                            + "-07-21 enrolledAt=" + start + " programEnrollmentStatus=ENROLLED closure=GOES_TO_FINAL/-",
                    csv.studentDni(1) + " " + csv.sectionCode(0) + " attempt=1 REGULAR score=- type=- finalDate=- enrolledAt=" + start
                            + " programEnrollmentStatus=ENROLLED closure=GOES_TO_FINAL/-",
                    csv.studentDni(1) + " " + csv.sectionCode(1) + " attempt=1 FAILED score=3.00 type=FINAL_EXAM finalDate=" + year
                            + "-07-22 enrolledAt=" + start + " programEnrollmentStatus=ENROLLED closure=FAILED/-",
                    csv.studentDni(2) + " " + csv.sectionCode(2) + " attempt=1 WITHDRAWN score=- type=- finalDate=- enrolledAt=" + start
                            + " programEnrollmentStatus=COMPLETED closure=none",
                    csv.studentDni(3) + " " + csv.sectionCode(3) + " attempt=2 APPROVED score=9.00 type=PROMOTION finalDate=" + year
                            + "-07-23 enrolledAt=" + start + " programEnrollmentStatus=WITHDRAWN closure=PROMOTES/9.00",
                    csv.studentDni(4) + " " + csv.sectionCode(0) + " attempt=1 APPROVED score=10.00 type=PROMOTION finalDate=" + year
                            + "-07-24 enrolledAt=" + start + " programEnrollmentStatus=ENROLLED closure=PROMOTES/10.00");

            assertThat(jdbc.queryForList("select entity_type || ':' || count(*) from import_created_entity where batch_id = ? group by entity_type order by entity_type",
                    String.class, testBatch)).containsExactly("PROGRAM_ENROLLMENTS:6", "SECTION_ENROLLMENTS:7", "TERMS:2");
            assertThat(jdbc.queryForList("select entity_type || ':' || status || ':' || count(*) from import_row where batch_id = ? group by entity_type, status order by entity_type",
                    String.class, testBatch)).containsExactly("PROGRAM_ENROLLMENTS:COMMITTED:6", "SECTION_ENROLLMENTS:COMMITTED:7", "TERMS:COMMITTED:1");
            assertThat(jdbc.queryForObject("""
                    select count(*) from import_row r join subject_section_enrollment e on e.id = r.committed_entity_id
                    where r.batch_id = ? and r.entity_type = 'SECTION_ENROLLMENTS'
                    """, Integer.class, testBatch)).isEqualTo(7);
            assertThat(jdbc.queryForObject("""
                    select count(*) from import_row r join program_enrollment e on e.id = r.committed_entity_id
                    where r.batch_id = ? and r.entity_type = 'PROGRAM_ENROLLMENTS'
                    """, Integer.class, testBatch)).isEqualTo(6);

            rollback(testBatch).andExpect(jsonPath("$.status").value("ROLLED_BACK"));
            assertThat(jdbc.queryForList(PROGRAM_ENROLLMENTS_OF_BATCH, String.class, testBatch)).isEmpty();
            assertThat(jdbc.queryForObject("""
                    select count(*) from subject_section_enrollment e join student s on s.id = e.student_id join person p on p.id = s.person_id
                    where p.dni in (?, ?, ?, ?, ?, ?)
                    """, Integer.class, csv.studentDni(0), csv.studentDni(1), csv.studentDni(2), csv.studentDni(3), csv.studentDni(4),
                    csv.studentDni(5))).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from program_enrollment e join student s on s.id = e.student_id join person p on p.id = s.person_id
                    where p.dni in (?, ?, ?, ?, ?, ?)
                    """, Integer.class, csv.studentDni(0), csv.studentDni(1), csv.studentDni(2), csv.studentDni(3), csv.studentDni(4),
                    csv.studentDni(5))).isZero();
        } finally {
            rollback(structureBatch);
        }
    }

    private static String row(String dni, String program, String section, int attempt, String status, String score, String type,
            String finalDate) {
        return "DNI," + dni + "," + program + "," + section + "," + attempt + "," + status + "," + score + "," + type + "," + finalDate + "\n";
    }
}

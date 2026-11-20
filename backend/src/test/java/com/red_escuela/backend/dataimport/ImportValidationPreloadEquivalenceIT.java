package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class ImportValidationPreloadEquivalenceIT extends AbstractDataImportIT {
    private static final String ROWS_WITH_ERRORS_IN_STORED_ORDER = """
            select r.entity_type || ' ' || r.natural_key || ' => ' || r.status || ' [' || coalesce((
                select string_agg(e.value ->> 'column' || ':' || (e.value ->> 'code'), ',' order by e.position)
                from jsonb_array_elements(case when jsonb_typeof(r.errors) = 'array' then r.errors else '[]'::jsonb end)
                     with ordinality as e(value, position)), '') || ']'
            from import_row r
            where r.batch_id = ? and r.entity_type in ('SECTION_ENROLLMENTS', 'PROGRAM_ENROLLMENTS', 'SECTIONS')
            order by r.entity_type, r.natural_key
            """;

    @Test
    void everyErrorOfSectionEnrollmentsProgramEnrollmentsAndSectionsKeepsItsStatusAndItsOrder() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZQ", year, firstDni, 6, 4);
        String program = csv.program();
        String missingProgram = "Inexistente " + suffix;
        String unknownDni = String.valueOf(firstDni + 900);
        String enrolledBeforeFirstClass = year + "-02-01";
        String finalDate = year + "-07-20";

        long structureBatch = createBatch();
        long historyBatch = createBatch();
        long testBatch = createBatch();
        try {
            importBaseStructure(structureBatch, csv, HistoricalDatasetCsv.PROGRAM_ENROLLMENT_HEADER
                    + programEnrollment(0, csv) + programEnrollment(1, csv) + programEnrollment(2, csv)
                    + programEnrollment(3, csv) + programEnrollment(4, csv));
            upload(historyBatch, "SECTION_ENROLLMENTS", HistoricalDatasetCsv.SECTION_ENROLLMENT_HEADER
                    + sectionEnrollment(csv.studentDni(0), program, csv.sectionCode(0), 1, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(0), program, csv.sectionCode(1), 1, "8,50", finalDate));
            validate(historyBatch).andExpect(jsonPath("$.status").value("VALIDATED"));
            commit(historyBatch).andExpect(jsonPath("$.status").value("COMMITTED"));

            upload(testBatch, "TERMS", HistoricalDatasetCsv.TERM_HEADER
                    + year + ",FIRST_SEMESTER,Primer cuatrimestre," + year + "-03-15,15/07/" + year + "," + program + "\n"
                    + year + ",FIRST_SEMESTER,Primer cuatrimestre," + year + "-03-15,15/07/" + year + ",Otra carrera " + suffix + "\n");
            upload(testBatch, "SECTIONS", HistoricalDatasetCsv.SECTION_HEADER
                    + "AMB-" + suffix + "," + csv.subjectCode(0) + "," + year + ",FIRST_SEMESTER,Ambigua,MORNING," + csv.instructorDni() + ",,,,,\n"
                    + "NT-" + suffix + "," + csv.subjectCode(0) + "," + (year + 3) + ",FIRST_SEMESTER,Sin ciclo,MORNING," + csv.instructorDni() + ",,,,,\n");
            upload(testBatch, "PROGRAM_ENROLLMENTS", HistoricalDatasetCsv.PROGRAM_ENROLLMENT_HEADER
                    + programEnrollment(5, csv)
                    + "DNI," + csv.studentDni(0) + "," + program + "," + year + "-03-02,ENROLLED,,,,\n"
                    + "DNI," + csv.studentDni(1) + "," + missingProgram + "," + year + "-03-01,ENROLLED,,,,\n"
                    + "DNI," + unknownDni + "," + program + "," + year + "-03-01,ENROLLED,,,,\n");
            upload(testBatch, "SECTION_ENROLLMENTS", HistoricalDatasetCsv.SECTION_ENROLLMENT_HEADER
                    + sectionEnrollment(csv.studentDni(1), program, csv.sectionCode(0), 1, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(0), program, csv.sectionCode(0), 2, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(0), program, csv.sectionCode(1), 1, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(2), program, csv.sectionCode(0), 1, "8,50", enrolledBeforeFirstClass)
                    + sectionEnrollment(csv.studentDni(3), program, csv.sectionCode(2), 1, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(3), program, csv.sectionCode(2), 2, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(4), missingProgram, csv.sectionCode(0), 1, "11", finalDate)
                    + sectionEnrollment(csv.studentDni(5), program, csv.sectionCode(0), 1, "8,50", finalDate)
                    + sectionEnrollment(csv.studentDni(4), program, "NOPE-" + suffix, 1, "8,50", finalDate));

            validate(testBatch).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));

            assertThat(jdbc.queryForList(ROWS_WITH_ERRORS_IN_STORED_ORDER, String.class, testBatch)).containsExactlyInAnyOrder(
                    "PROGRAM_ENROLLMENTS " + programKey(csv, 5) + " => VALID []",
                    "PROGRAM_ENROLLMENTS " + programKey(csv, 0) + " => CONFLICT [programName:PROGRAM_ENROLLMENT_ALREADY_EXISTS]",
                    "PROGRAM_ENROLLMENTS DNI:" + csv.studentDni(1) + "|" + missingProgram + " => ERROR [programName:PROGRAM_NOT_FOUND]",
                    "PROGRAM_ENROLLMENTS DNI:" + unknownDni + "|" + program + " => ERROR [dni:STUDENT_NOT_FOUND]",
                    "SECTIONS AMB-" + suffix + " => ERROR [closedOn:CLOSED_ON_REQUIRED,year:TERM_AMBIGUOUS]",
                    "SECTIONS NT-" + suffix + " => ERROR [closedOn:CLOSED_ON_REQUIRED,year:TERM_NOT_FOUND]",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 1, 0, 1) + " => VALID []",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 0, 0, 2) + " => CONFLICT [sectionCode:SECTION_ENROLLMENT_ALREADY_EXISTS]",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 0, 1, 1) + " => UNCHANGED []",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 2, 0, 1) + " => ERROR [finalDate:DATE_BEFORE_ENROLLMENT]",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 3, 2, 1) + " => VALID []",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 3, 2, 2) + " => ERROR [studentSection:STUDENTSECTION_DUPLICATED_IN_FILE]",
                    "SECTION_ENROLLMENTS DNI:" + csv.studentDni(4) + "|" + missingProgram + "|" + csv.sectionCode(0)
                            + "|1 => ERROR [finalScore:FINAL_SCORE_OUT_OF_RANGE,programName:PROGRAM_ENROLLMENT_NOT_FOUND]",
                    "SECTION_ENROLLMENTS " + enrollmentKey(csv, 5, 0, 1) + " => VALID []",
                    "SECTION_ENROLLMENTS DNI:" + csv.studentDni(4) + "|" + program + "|NOPE-" + suffix
                            + "|1 => ERROR [sectionCode:SECTION_NOT_FOUND]");
        } finally {
            rollback(historyBatch);
            rollback(structureBatch);
        }
    }

    private String programEnrollment(int student, HistoricalDatasetCsv csv) {
        return "DNI," + csv.studentDni(student) + "," + csv.program() + "," + year + "-03-01,ENROLLED,,,,\n";
    }

    private static String sectionEnrollment(String dni, String program, String section, int attempt, String score, String finalDate) {
        return "DNI," + dni + "," + program + "," + section + "," + attempt + ",APPROVED,\"" + score + "\",PROMOTION," + finalDate + "\n";
    }

    private static String programKey(HistoricalDatasetCsv csv, int student) {
        return "DNI:" + csv.studentDni(student) + "|" + csv.program();
    }

    private static String enrollmentKey(HistoricalDatasetCsv csv, int student, int section, int attempt) {
        return programKey(csv, student) + "|" + csv.sectionCode(section) + "|" + attempt;
    }
}

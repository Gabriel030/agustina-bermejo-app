package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentRef;
import com.red_escuela.backend.enrollment.api.StudentProgram;
import com.red_escuela.backend.evaluation.api.HistoricalClosureRecorder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HistoricalCommitFindersIT extends AbstractDataImportIT {
    private static final int UNKNOWN_KEYS = 2_500;

    @Autowired
    private HistoricalSectionRecorder sectionRecorder;
    @Autowired
    private HistoricalEnrollmentRecorder enrollmentRecorder;
    @Autowired
    private HistoricalClosureRecorder closureRecorder;

    @Test
    void commitFindersResolveIdsDatesAndClosuresForKeysInDifferentChunks() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZG", year, firstDni, 3, 2);
        long structureBatch = createBatch();
        try {
            importBaseStructure(structureBatch, csv);

            Long firstSection = sectionRecorder.findSectionIdByCode(csv.sectionCode(0)).orElseThrow();
            Long secondSection = sectionRecorder.findSectionIdByCode(csv.sectionCode(1)).orElseThrow();
            List<Long> sectionIds = padded(firstSection, secondSection, firstSection, index -> -1L - index);

            Map<Long, LocalDateTime> termStarts = sectionRecorder.findTermStartsBySectionIds(sectionIds);
            assertThat(termStarts).containsOnlyKeys(firstSection, secondSection);
            assertThat(termStarts.get(firstSection).toLocalDate()).isEqualTo(LocalDate.of(year, 3, 15));
            assertThat(termStarts.get(secondSection).toLocalDate()).isEqualTo(LocalDate.of(year, 3, 15));

            Map<Long, Long> closureIds = closureRecorder.findClosureIdsBySectionIds(sectionIds);
            assertThat(closureIds).containsOnlyKeys(firstSection, secondSection);
            assertThat(closureIds.get(firstSection)).isEqualTo(jdbc.queryForObject(
                    "select id from commission_closure where section_kind = 'PROGRAM' and section_id = ?", Long.class, firstSection));
            assertThat(closureIds.get(secondSection)).isNotEqualTo(closureIds.get(firstSection));

            Long programId = jdbc.queryForObject("select id from program where name = ?", Long.class, csv.program());
            Long firstStudent = jdbc.queryForObject(
                    "select s.id from student s join person p on p.id = s.person_id where p.dni = ?", Long.class, csv.studentDni(0));
            Long lastStudent = jdbc.queryForObject(
                    "select s.id from student s join person p on p.id = s.person_id where p.dni = ?", Long.class, csv.studentDni(2));
            List<StudentProgram> pairs = padded(new StudentProgram(firstStudent, programId), new StudentProgram(lastStudent, programId),
                    new StudentProgram(lastStudent, programId + 1_000_000L), index -> new StudentProgram(-1L - index, programId));
            Map<StudentProgram, ProgramEnrollmentRef> refs = enrollmentRecorder.findProgramEnrollmentRefs(pairs);
            assertThat(refs).containsOnlyKeys(new StudentProgram(firstStudent, programId), new StudentProgram(lastStudent, programId));
            ProgramEnrollmentRef firstRef = refs.get(new StudentProgram(firstStudent, programId));
            assertThat(firstRef.enrollmentDate()).isEqualTo(LocalDate.of(year, 3, 1));
            assertThat(firstRef.id()).isEqualTo(jdbc.queryForObject(
                    "select id from program_enrollment where student_id = ? and program_id = ?", Long.class, firstStudent, programId));
        } finally {
            rollback(structureBatch);
        }
    }

    private static <T> List<T> padded(T first, T middle, T last, IntFunction<T> unknown) {
        List<T> keys = new ArrayList<>();
        keys.add(first);
        IntStream.range(0, UNKNOWN_KEYS / 2).mapToObj(unknown).forEach(keys::add);
        keys.add(middle);
        IntStream.range(UNKNOWN_KEYS / 2, UNKNOWN_KEYS).mapToObj(unknown).forEach(keys::add);
        keys.add(last);
        return keys;
    }
}

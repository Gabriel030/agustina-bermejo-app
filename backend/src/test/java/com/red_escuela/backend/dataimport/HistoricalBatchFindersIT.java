package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.api.StudentProgram;
import com.red_escuela.backend.enrollment.api.StudentSection;
import com.red_escuela.backend.identity.api.DocumentKey;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HistoricalBatchFindersIT extends AbstractDataImportIT {
    private static final int UNKNOWN_KEYS = 2_500;

    @Autowired
    private HistoricalPersonRecorder personRecorder;
    @Autowired
    private HistoricalProgramRecorder programRecorder;
    @Autowired
    private HistoricalSectionRecorder sectionRecorder;
    @Autowired
    private HistoricalEnrollmentRecorder enrollmentRecorder;

    @Test
    void everyBatchFinderResolvesKeysThatFallInDifferentChunks() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZF", year, firstDni, 3, 2);
        long structureBatch = createBatch();
        long historyBatch = createBatch();
        try {
            importBaseStructure(structureBatch, csv);
            upload(historyBatch, "SECTION_ENROLLMENTS", csv.sectionEnrollments(0, 1));
            validate(historyBatch);
            commit(historyBatch);

            List<DocumentKey> documents = padded(new DocumentKey(DocumentType.DNI, csv.studentDni(0)),
                    new DocumentKey(DocumentType.DNI, csv.studentDni(1)), new DocumentKey(DocumentType.DNI, csv.studentDni(2)),
                    index -> new DocumentKey(DocumentType.DNI, "9" + index));
            Map<DocumentKey, Long> studentIds = personRecorder.findStudentIdsByDocuments(documents);
            assertThat(studentIds).containsOnlyKeys(new DocumentKey(DocumentType.DNI, csv.studentDni(0)),
                    new DocumentKey(DocumentType.DNI, csv.studentDni(1)), new DocumentKey(DocumentType.DNI, csv.studentDni(2)));
            assertThat(studentIds.values()).doesNotContainNull().doesNotHaveDuplicates();

            List<String> programNames = padded(csv.program(), csv.program() + " bis", csv.program() + " ter", index -> "Carrera " + index);
            Map<String, Long> programIds = programRecorder.findProgramIdsByNames(programNames);
            assertThat(programIds).containsOnlyKeys(csv.program());
            assertThat(programIds.get(csv.program())).isEqualTo(programRecorder.findProgramIdByName(csv.program()).orElseThrow());

            List<String> sectionCodes = padded(csv.sectionCode(0), csv.sectionCode(1), "sin-comision", index -> "COM-" + index);
            Map<String, Long> sectionIds = sectionRecorder.findSectionIdsByCodes(sectionCodes);
            assertThat(sectionIds).containsOnlyKeys(csv.sectionCode(0), csv.sectionCode(1));
            assertThat(sectionIds.get(csv.sectionCode(1))).isEqualTo(sectionRecorder.findSectionIdByCode(csv.sectionCode(1)).orElseThrow());

            Long programId = programIds.get(csv.program());
            Long firstStudent = studentIds.get(new DocumentKey(DocumentType.DNI, csv.studentDni(0)));
            Long lastStudent = studentIds.get(new DocumentKey(DocumentType.DNI, csv.studentDni(2)));
            List<StudentProgram> programPairs = padded(new StudentProgram(firstStudent, programId), new StudentProgram(lastStudent, programId),
                    new StudentProgram(lastStudent, programId + 1_000_000L), index -> new StudentProgram(-1L - index, programId));
            Map<StudentProgram, LocalDate> enrollmentDates = enrollmentRecorder.findProgramEnrollmentDates(programPairs);
            assertThat(enrollmentDates).containsOnlyKeys(new StudentProgram(firstStudent, programId), new StudentProgram(lastStudent, programId));
            assertThat(enrollmentDates.get(new StudentProgram(firstStudent, programId))).isEqualTo(LocalDate.of(year, 3, 1));

            Long firstSection = sectionIds.get(csv.sectionCode(0));
            Long secondSection = sectionIds.get(csv.sectionCode(1));
            List<StudentSection> sectionPairs = padded(new StudentSection(firstStudent, firstSection), new StudentSection(firstStudent, secondSection),
                    new StudentSection(lastStudent, firstSection), index -> new StudentSection(-1L - index, firstSection));
            assertThat(enrollmentRecorder.findExistingSectionEnrollments(sectionPairs))
                    .containsExactlyInAnyOrder(new StudentSection(firstStudent, firstSection), new StudentSection(firstStudent, secondSection));
        } finally {
            rollback(historyBatch);
            rollback(structureBatch);
        }
    }

    private static <T> List<T> padded(T first, T middle, T last, java.util.function.IntFunction<T> unknown) {
        List<T> keys = new ArrayList<>();
        keys.add(first);
        IntStream.range(0, UNKNOWN_KEYS / 2).mapToObj(unknown).forEach(keys::add);
        keys.add(middle);
        IntStream.range(UNKNOWN_KEYS / 2, UNKNOWN_KEYS).mapToObj(unknown).forEach(keys::add);
        keys.add(last);
        return keys;
    }
}

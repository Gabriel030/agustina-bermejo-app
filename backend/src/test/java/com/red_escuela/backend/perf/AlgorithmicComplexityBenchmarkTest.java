package com.red_escuela.backend.perf;

import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.enrollment.api.SubjectApprovalLookup;
import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalOutcome;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

@EnabledIfSystemProperty(named = "perf.benchmark", matches = "true")
class AlgorithmicComplexityBenchmarkTest {

    private static final int[] FACTORS = {1, 10, 100};
    private static final int RUNS = 5;

    private static long medianNanos(IntConsumer work, int size) {
        for (int i = 0; i < 3; i++) {
            work.accept(size);
        }
        long[] samples = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            work.accept(size);
            samples[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(samples);
        return samples[RUNS / 2];
    }

    private static void report(String id, String variable, int base, IntConsumer work) {
        StringBuilder line = new StringBuilder("BENCH " + id + " " + variable);
        long previous = 0;
        for (int factor : FACTORS) {
            int size = base * factor;
            long nanos = medianNanos(work, size);
            line.append(String.format(" | n=%d: %.3f ms", size, nanos / 1_000_000.0));
            if (previous > 0) {
                line.append(String.format(" (x%.1f)", (double) nanos / previous));
            }
            previous = nanos;
        }
        System.out.println(line);
    }

    @Test
    void b1_justificationDeadlinesPerDistinctClassDate() {
        BusinessDayCalendar calendar = date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
        report("B1", "distinct class dates", 40, size -> {
            LocalDate start = LocalDate.of(2026, 3, 1);
            Map<LocalDate, LocalDate> deadlines = IntStream.range(0, size).mapToObj(start::plusDays).distinct()
                    .collect(Collectors.toMap(d -> d, d -> calendar.nthBusinessDayAfter(d, 5)));
            if (deadlines.isEmpty()) {
                throw new IllegalStateException();
            }
        });
    }

    private static final class ProgressFixture {
        final AtomicInteger repositoryCalls = new AtomicInteger();
        final ProgramProgressReader reader;

        ProgressFixture(int entries) {
            ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class, withSettings().stubOnly());
            SubjectSectionEnrollmentRepository sectionRepository = mock(SubjectSectionEnrollmentRepository.class, withSettings().stubOnly());
            ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class, withSettings().stubOnly());
            ProgramEnrollment enrollment = mock(ProgramEnrollment.class, withSettings().stubOnly());
            Program program = mock(Program.class, withSettings().stubOnly());
            Mockito.when(enrollment.getId()).thenReturn(1L);
            Mockito.when(enrollment.getProgram()).thenReturn(program);
            Mockito.when(program.getId()).thenReturn(1L);
            Mockito.when(program.getTotalCreditsRequired()).thenReturn(1);
            Mockito.when(enrollmentRepository.findById(any())).thenReturn(Optional.of(enrollment));
            List<ProgramSubjectView> views = new ArrayList<>();
            for (long i = 1; i <= entries; i++) {
                views.add(new ProgramSubjectView(i, i, "S" + i, 4, 1, null, null));
            }
            Mockito.when(curriculumFinder.findProgramSubjects(anyLong())).thenReturn(views);
            Mockito.when(curriculumFinder.findElectiveGroupRequirements(anyLong())).thenReturn(List.of());
            SubjectSectionEnrollment found = mock(SubjectSectionEnrollment.class, withSettings().stubOnly());
            Mockito.when(sectionRepository.findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(any(), any(), any()))
                    .thenAnswer(invocation -> {
                        repositoryCalls.incrementAndGet();
                        return List.of(found);
                    });
            Mockito.when(sectionRepository.findByProgramEnrollmentId(any())).thenReturn(List.of());
            Mockito.when(sectionRepository.existsByProgramEnrollmentIdAndStatusIn(any(), any())).thenReturn(false);
            reader = new ProgramProgressReader(enrollmentRepository, sectionRepository, curriculumFinder,
                    new SubjectApprovalLookup(sectionRepository, Mockito.mock(SubjectEquivalenceRepository.class)));
        }
    }

    @Test
    void b2_programProgressCalculateProgress() {
        AtomicInteger calls = new AtomicInteger();
        report("B2", "curriculum entries (all approved)", 40, size -> {
            ProgressFixture fixture = new ProgressFixture(size);
            fixture.reader.calculateProgress(1L);
            calls.set(fixture.repositoryCalls.get());
        });
        System.out.println("BENCH B2 repository calls at last size=" + calls.get());
    }

    @Test
    void b3_programProgressHasOnlyFinalExamsLeft() {
        AtomicInteger calls = new AtomicInteger();
        report("B3", "curriculum entries (all coursed)", 40, size -> {
            ProgressFixture fixture = new ProgressFixture(size);
            fixture.reader.hasOnlyFinalExamsLeft(1L);
            calls.set(fixture.repositoryCalls.get());
        });
        System.out.println("BENCH B3 repository calls at last size=" + calls.get());
    }

    @Test
    void b4_courseProgressPendingModulesNoneMatch() {
        report("B4", "course modules (none approved)", 10, size -> {
            List<long[]> modules = new ArrayList<>();
            List<long[]> enrollments = new ArrayList<>();
            for (long i = 0; i < size; i++) {
                modules.add(new long[]{i});
                enrollments.add(new long[]{i + size, 0});
            }
            List<long[]> pending = modules.stream()
                    .filter(module -> enrollments.stream().noneMatch(e -> e[0] == module[0] && e[1] == 1))
                    .toList();
            if (pending.size() != size) {
                throw new IllegalStateException();
            }
        });
    }

    private record Slot(DayOfWeek day, LocalTime start, LocalTime end) {}

    @Test
    void b5_scheduleOverlapSlotPairs() {
        report("B5", "slots per section (no overlap)", 3, size -> {
            List<Slot> target = IntStream.range(0, size).mapToObj(i -> new Slot(DayOfWeek.MONDAY, LocalTime.of(0, 0).plusMinutes(i * 2L), LocalTime.of(0, 0).plusMinutes(i * 2L + 1))).toList();
            List<Slot> other = IntStream.range(0, size).mapToObj(i -> new Slot(DayOfWeek.MONDAY, LocalTime.of(0, 0).plusMinutes(i * 2L + 1), LocalTime.of(0, 0).plusMinutes(i * 2L + 2))).toList();
            boolean found = false;
            for (Slot t : target) {
                for (Slot o : other) {
                    if (t.day() == o.day() && t.start().isBefore(o.end()) && o.start().isBefore(t.end())) {
                        found = true;
                    }
                }
            }
            if (found) {
                throw new IllegalStateException();
            }
        });
    }

    @Test
    void b6_pendingTransferApprovalOutcomeListContains() {
        report("B6", "invoices settled by one receipt", 100, size -> {
            List<Long> ids = LongStreamBoxed.range(size);
            boolean outside = new PendingTransferApprovalOutcome(1L, ids, ids, List.of()).settledAnInvoiceOutsideTheReceipt();
            if (outside) {
                throw new IllegalStateException();
            }
        });
    }

    private static final class LongStreamBoxed {
        static List<Long> range(int size) {
            return java.util.stream.LongStream.range(0, size).boxed().toList();
        }
    }

    @Test
    void b7_audienceSetOperations() {
        report("B7", "persons per segment (retainAll + removeAll on HashSet)", 700, size -> {
            Set<Long> first = new HashSet<>(LongStreamBoxed.range(size));
            Set<Long> second = new HashSet<>(java.util.stream.LongStream.range(size / 2, size + size / 2).boxed().toList());
            Set<Long> result = new HashSet<>(first);
            result.retainAll(second);
            Set<Long> active = new HashSet<>(first);
            active.removeAll(second);
            if (result.size() + active.size() != size) {
                throw new IllegalStateException();
            }
        });
    }

    @Test
    void b8_attendanceHistoryRowsPerStudent() {
        int classDates = 40;
        report("B8", "students in section (x40 class dates, group+sort)", 100, size -> {
            LocalDate start = LocalDate.of(2026, 3, 2);
            List<long[]> attendances = new ArrayList<>();
            for (long s = 0; s < size; s++) {
                for (int d = 0; d < classDates; d++) {
                    attendances.add(new long[]{s, d});
                }
            }
            Map<Long, List<long[]>> byEnrollment = attendances.stream().collect(Collectors.groupingBy(a -> a[0]));
            Map<LocalDate, LocalDate> deadlines = new HashMap<>();
            for (int d = 0; d < classDates; d++) {
                deadlines.put(start.plusDays(d), start.plusDays(d + 5L));
            }
            List<String> rows = LongStreamBoxed.range(size).stream()
                    .map(id -> "student-" + id + "-" + byEnrollment.getOrDefault(id, List.of()).stream()
                            .collect(Collectors.toMap(a -> start.plusDays(a[1]), a -> deadlines.get(start.plusDays(a[1])), (a, b) -> b)).size())
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (rows.size() != size) {
                throw new IllegalStateException();
            }
        });
    }
}

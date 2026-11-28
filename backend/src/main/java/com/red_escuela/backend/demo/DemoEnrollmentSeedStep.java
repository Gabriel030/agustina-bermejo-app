package com.red_escuela.backend.demo;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoEnrollmentSeedStep implements DemoSeedStep {
    public static final int STEP_ORDER = 30;

    public static final int SECTIONS_PER_STUDENT = 3;
    private static final int COURSE_STUDENT_INTERVAL = 4;

    private final DemoSeedSession session;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentBillingModePort billingMode;
    private final Map<Long, Long> cohortIdsByProgramId = new HashMap<>();

    public DemoEnrollmentSeedStep(DemoSeedSession session, JdbcTemplate jdbcTemplate, CurrentBillingModePort billingMode) {
        this.session = session;
        this.jdbcTemplate = jdbcTemplate;
        this.billingMode = billingMode;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public String label() {
        return "inscripciones";
    }

    @Override
    public void seed(DemoDataset dataset) {
        List<DemoCatalog.ProgramSpec> programs = dataset.programs();
        for (int index = 0; index < dataset.studentCount(); index++) {
            long studentId = findStudentId(DemoPeopleSeedStep.studentUsername(index));
            DemoCatalog.ProgramSpec program = programs.get(index % programs.size());
            enrollInProgram(studentId, program);
            if (index % COURSE_STUDENT_INTERVAL == 0 && !dataset.courses().isEmpty()) {
                enrollInCourse(studentId, dataset.courses().get(index % dataset.courses().size()));
            }
        }
    }

    private long findStudentId(String username) {
        Long studentId = DemoPeopleSeedStep.findStudentId(session, username);
        if (studentId == null) {
            throw new IllegalStateException("No existe el alumno " + username);
        }
        return studentId;
    }

    private void enrollInProgram(long studentId, DemoCatalog.ProgramSpec program) {
        long programId = session.programIdsByName().get(program.name());
        long programEnrollmentId = session.api().post(DemoPersona.ADMIN, "/students/" + studentId + "/program-enrollments",
                Map.of("programId", programId, "cohortId", findOrCreateCohort(programId))).get("id").asLong();
        List<Long> sectionIds = session.firstYearSectionIdsByProgram().get(program.name());
        for (Long sectionId : sectionIds.subList(0, Math.min(SECTIONS_PER_STUDENT, sectionIds.size()))) {
            session.api().post(DemoPersona.ADMIN, "/program-enrollments/" + programEnrollmentId + "/section-enrollments",
                    Map.of("sectionId", sectionId));
        }
    }

    private long findOrCreateCohort(long programId) {
        return cohortIdsByProgramId.computeIfAbsent(programId, id -> {
            long termId = findAnnualTermId(2026);
            long preceptorId = jdbcTemplate.queryForObject("select id from person where username = 'preceptor.demo'", Long.class);
            Map<String, Object> cohortBody = Map.of("intake", "FIRST", "admissionStart", "2020-01-01", "admissionEnd", "2099-12-31",
                    "cursadaStart", "2026-03-01", "responsiblePreceptorIds", List.of(preceptorId));
            long cohortId;
            if (billingMode.currentMode() == BillingMode.OFF) {
                cohortId = session.api().post(DemoPersona.ADMIN, "/programs/" + id + "/terms/" + termId + "/cohorts", cohortBody).get("id").asLong();
            } else {
                long templateId = session.api().post(DemoPersona.DIRECTOR, "/programs/" + id + "/year-billing-templates",
                        Map.of("termId", termId)).get("id").asLong();
                cohortId = session.api().post(DemoPersona.ADMIN, "/year-billing-templates/" + templateId + "/cohorts", cohortBody).get("id").asLong();
            }
            jdbcTemplate.update("update program_cohort set opened_at = now() where id = ?", cohortId);
            return cohortId;
        });
    }

    private long findAnnualTermId(int year) {
        for (JsonNode term : session.api().get(DemoPersona.ADMIN, "/terms")) {
            if (term.path("year").asInt() == year && "ANNUAL".equals(term.path("type").asString(""))) {
                return term.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe el ciclo lectivo " + year);
    }

    private void enrollInCourse(long studentId, DemoCatalog.CourseSpec course) {
        session.api().post(DemoPersona.ADMIN, "/students/" + studentId + "/course-enrollments",
                Map.of("courseId", session.courseIdsByName().get(course.name())));
    }
}

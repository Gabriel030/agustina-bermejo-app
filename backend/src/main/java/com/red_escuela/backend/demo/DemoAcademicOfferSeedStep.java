package com.red_escuela.backend.demo;

import com.red_escuela.backend.identity.api.CohortResponsibleAssigner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoAcademicOfferSeedStep implements DemoSeedStep {
    public static final int STEP_ORDER = 20;
    public static final String SCHEME_NAME = "Esquema demo v2: 2 parciales y final";

    private static final int CREDITS_PER_SUBJECT = 5;
    private static final int SUBJECTS_PER_TERM_HALF = 3;

    private final DemoSeedSession session;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final CohortResponsibleAssigner preceptorAssigner;
    private final Map<String, Long> subjectIdsByName = new LinkedHashMap<>();
    private long leadTeacherId;
    private long schemeId;

    public DemoAcademicOfferSeedStep(DemoSeedSession session, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                      CohortResponsibleAssigner preceptorAssigner) {
        this.session = session;
        this.preceptorAssigner = preceptorAssigner;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public String label() {
        return "oferta académica";
    }

    @Override
    public void seed(DemoDataset dataset) {
        subjectIdsByName.clear();
        leadTeacherId = findLeadTeacherId();
        schemeId = findOrCreateScheme();
        boolean firstProgram = true;
        for (DemoCatalog.ProgramSpec program : dataset.programs()) {
            seedProgram(program, firstProgram);
            firstProgram = false;
        }
        for (DemoCatalog.CourseSpec course : dataset.courses()) {
            seedCourse(course);
        }
    }

    private long findPreceptorId() {
        return jdbcTemplate.queryForObject("select id from person where username = 'preceptor.demo'", Long.class);
    }

    private long findLeadTeacherId() {
        for (JsonNode instructor : session.api().get(DemoPersona.ADMIN, "/instructors")) {
            if (DemoPersona.TEACHER_FULL_NAME.equals(instructor.path("name").asString(""))) {
                return instructor.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe el perfil de docente de " + DemoPersona.TEACHER);
    }

    private long findOrCreateScheme() {
        Long existing = session.findIdByName(session.api().get(DemoPersona.DIRECTOR, "/evaluation-schemes"), SCHEME_NAME);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> scheme = new LinkedHashMap<>();
        scheme.put("name", SCHEME_NAME);
        scheme.put("examCountExpected", 2);
        scheme.put("hasFinal", true);
        scheme.put("finalWeight", 0.5);
        scheme.put("aggregationMethod", "SIMPLE_AVERAGE");
        scheme.put("minRegularGrade", 4);
        scheme.put("minApprovalGrade", 6);
        return session.api().post(DemoPersona.DIRECTOR, "/evaluation-schemes", scheme).get("id").asLong();
    }

    private void ensureHolidaysLoaded(int year) {
        String prefix = year + "-";
        for (JsonNode holiday : session.api().get(DemoPersona.ADMIN, "/holidays")) {
            if (holiday.path("date").asString("").startsWith(prefix)) {
                return;
            }
        }
        session.api().post(DemoPersona.ADMIN, "/holidays",
                Map.of("date", year + "-05-01", "name", "Día del Trabajador", "type", "NATIONAL"));
    }

    private long findOrCreateAnnualTerm(int year) {
        for (JsonNode term : session.api().get(DemoPersona.ADMIN, "/terms")) {
            if (term.path("year").asInt() == year && "ANNUAL".equals(term.path("type").asString(""))
                    && "ACTIVE".equals(term.path("status").asString(""))) {
                return term.get("id").asLong();
            }
        }
        ensureHolidaysLoaded(year);
        Map<String, Object> term = new LinkedHashMap<>();
        term.put("year", year);
        term.put("description", "Ciclo lectivo " + year);
        term.put("type", "ANNUAL");
        term.put("startDate", year + "-03-01T00:00:00");
        term.put("endDate", year + "-12-20T00:00:00");
        term.put("enrollmentStart", (year - 1) + "-12-01T00:00:00");
        term.put("enrollmentEnd", year + "-12-20T00:00:00");
        long termId = session.api().post(DemoPersona.ADMIN, "/terms", term).get("id").asLong();
        session.api().post(DemoPersona.ADMIN, "/terms/" + termId + "/enrollment/open", null);
        return termId;
    }

    private void seedProgram(DemoCatalog.ProgramSpec spec, boolean assignPreceptor) {
        if (session.findIdByName(session.api().get(DemoPersona.ADMIN, "/programs"), spec.name()) != null) {
            throw new IllegalStateException("La carrera " + spec.name() + " ya existe sin el marcador de la demo");
        }
        int subjectCount = spec.subjectsByYear().stream().mapToInt(List::size).sum();
        Map<String, Object> program = new LinkedHashMap<>();
        program.put("name", spec.name());
        program.put("totalYears", spec.totalYears());
        program.put("totalCreditsRequired", subjectCount * CREDITS_PER_SUBJECT);
        program.put("price", Map.of("amount", spec.monthlyPrice().toPlainString(), "currency", "ARS"));
        program.put("enrollmentFeeAmount", Map.of("amount", spec.enrollmentFee().toPlainString(), "currency", "ARS"));
        long programId = session.api().post(DemoPersona.ADMIN, "/programs", program).get("id").asLong();
        session.programIdsByName().put(spec.name(), programId);
        long termId = findOrCreateAnnualTerm(2026);
        long cycleId = createCycle("programId", programId, termId);
        Map<String, Long> programSubjectIdsByName = new LinkedHashMap<>();
        for (int yearIndex = 0; yearIndex < spec.subjectsByYear().size(); yearIndex++) {
            List<String> subjects = spec.subjectsByYear().get(yearIndex);
            for (int position = 0; position < subjects.size(); position++) {
                long subjectId = findOrCreateSubject(subjects.get(position));
                programSubjectIdsByName.put(subjects.get(position), linkSubject(programId, subjectId, yearIndex + 1, position));
                long sectionId = createProgramSection(cycleId, subjectId);
                if (yearIndex == 0) {
                    if (assignPreceptor) {
                        long preceptorId = findPreceptorId();
                        preceptorAssigner.assignToSubjectSection(sectionId, List.of(preceptorId), preceptorId);
                    }
                    session.firstYearSectionIdsByProgram().computeIfAbsent(spec.name(), name -> new ArrayList<>()).add(sectionId);
                }
            }
        }
        approveCycle(cycleId);
        seedPrerequisites(programSubjectIdsByName);
    }

    private long findOrCreateSubject(String name) {
        Long cached = subjectIdsByName.get(name);
        if (cached != null) {
            return cached;
        }
        Long existing = session.findIdByName(session.api().get(DemoPersona.ADMIN, "/subjects"), name);
        if (existing != null) {
            subjectIdsByName.put(name, existing);
            return existing;
        }
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("name", name);
        draft.put("code", subjectCode(name));
        draft.put("weeklyHours", 4);
        draft.put("gradeMode", "NUMERIC");
        long subjectId = session.api().post(DemoPersona.ADMIN, "/subjects", draft).get("id").asLong();
        session.api().post(DemoPersona.ADMIN, "/subjects/" + subjectId + "/submit-for-approval", null);
        session.api().post(DemoPersona.DIRECTOR, "/subjects/" + subjectId + "/approve", null);
        subjectIdsByName.put(name, subjectId);
        return subjectId;
    }

    static String subjectCode(String name) {
        int position = DemoCatalog.distinctSubjectNames().indexOf(name);
        String sequence = Integer.toString(position + 1, 36).toUpperCase(java.util.Locale.ROOT);
        return "S" + "0".repeat(3 - sequence.length()) + sequence;
    }

    private long linkSubject(long programId, long subjectId, int curricularYear, int position) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("subjectId", subjectId);
        link.put("category", "MANDATORY");
        link.put("curricularYear", curricularYear);
        link.put("curricularTerm", position < SUBJECTS_PER_TERM_HALF ? "FIRST" : "SECOND");
        link.put("credits", CREDITS_PER_SUBJECT);
        return session.api().post(DemoPersona.ADMIN, "/programs/" + programId + "/subjects", link).get("id").asLong();
    }

    private long createCycle(String ownerKey, long ownerId, long termId) {
        return session.api().post(DemoPersona.ADMIN, "/academic-offering-cycles", Map.of(ownerKey, ownerId, "termId", termId)).get("id").asLong();
    }

    private long createProgramSection(long cycleId, long subjectId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("name", "Comisión A");
        section.put("subjectId", subjectId);
        section.put("capacity", 30);
        section.put("shift", "MORNING");
        long sectionId = session.api().post(DemoPersona.ADMIN, "/academic-offering-cycles/" + cycleId + "/sections", section).asLong();
        session.api().put(DemoPersona.ADMIN, "/program-sections/" + sectionId, Map.of("evaluationSchemeId", schemeId));
        session.api().post(DemoPersona.ADMIN, "/program-sections/" + sectionId + "/instructors",
                Map.of("instructorId", leadTeacherId, "role", "LEAD"));
        return sectionId;
    }

    private void approveCycle(long cycleId) {
        session.api().post(DemoPersona.ADMIN, "/academic-offering-cycles/" + cycleId + "/submit", null);
        session.api().post(DemoPersona.DIRECTOR, "/academic-offering-cycles/" + cycleId + "/approve", null);
    }

    private void seedPrerequisites(Map<String, Long> programSubjectIdsByName) {
        programSubjectIdsByName.forEach((subjectName, programSubjectId) -> {
            String prerequisiteName = DemoCatalog.prerequisiteOf(subjectName);
            Long prerequisiteId = prerequisiteName == null ? null : programSubjectIdsByName.get(prerequisiteName);
            if (prerequisiteId != null) {
                session.api().post(DemoPersona.ADMIN, "/program-subjects/" + programSubjectId + "/prerequisites",
                        Map.of("prerequisiteProgramSubjectId", prerequisiteId, "requirementType", "APPROVED"));
            }
        });
    }

    private void seedCourse(DemoCatalog.CourseSpec spec) {
        long termId = findOrCreateAnnualTerm(spec.termYear());
        Map<String, Object> course = new LinkedHashMap<>();
        course.put("name", spec.name());
        course.put("termId", termId);
        course.put("schedule", spec.schedule());
        course.put("capacity", spec.capacity());
        course.put("price", Map.of("amount", spec.price().toPlainString(), "currency", "ARS"));
        course.put("durationMonths", spec.durationMonths());
        course.put("teachingMode", spec.teachingMode());
        long preceptorId = findPreceptorId();
        course.put("responsiblePreceptorIds", List.of(preceptorId));
        long courseId = session.api().post(DemoPersona.ADMIN, "/courses", course).get("id").asLong();
        session.courseIdsByName().put(spec.name(), courseId);
        long cycleId = createCycle("courseId", courseId, termId);
        int order = 1;
        for (String module : spec.subjects()) {
            long moduleId = session.api().post(DemoPersona.ADMIN, "/courses/" + courseId + "/subjects",
                    Map.of("name", module, "sortOrder", order++)).get("id").asLong();
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("name", "Comisión única");
            section.put("courseSubjectId", moduleId);
            section.put("capacity", spec.capacity());
            section.put("shift", "MORNING");
            long sectionId = session.api().post(DemoPersona.ADMIN, "/academic-offering-cycles/" + cycleId + "/sections", section).asLong();
            session.api().put(DemoPersona.ADMIN, "/course-sections/" + sectionId, Map.of("evaluationSchemeId", schemeId));
            session.api().post(DemoPersona.ADMIN, "/course-sections/" + sectionId + "/instructors",
                    Map.of("instructorId", leadTeacherId, "role", "LEAD"));
            preceptorAssigner.assignToCourseSection(sectionId, List.of(preceptorId), preceptorId);
        }
        approveCycle(cycleId);
    }
}

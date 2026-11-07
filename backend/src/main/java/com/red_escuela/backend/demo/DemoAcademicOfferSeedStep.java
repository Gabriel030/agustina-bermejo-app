package com.red_escuela.backend.demo;

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
    private static final String[] SCHEDULES = {"Lunes y miércoles 18:00 a 20:00", "Martes y jueves 18:00 a 20:00", "Viernes 18:00 a 22:00"};

    private final DemoSeedSession session;
    private final Map<String, Long> subjectIdsByName = new LinkedHashMap<>();
    private final Map<Long, Long> sectionIdsBySubjectId = new LinkedHashMap<>();
    private List<Long> instructorIds = List.of();
    private int leadTeacherCursor;
    private long schemeId;

    public DemoAcademicOfferSeedStep(DemoSeedSession session) {
        this.session = session;
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
        sectionIdsBySubjectId.clear();
        leadTeacherCursor = 0;
        instructorIds = findInstructorIds();
        schemeId = findOrCreateScheme();
        for (DemoCatalog.ProgramSpec program : dataset.programs()) {
            seedProgram(program);
        }
        for (DemoCatalog.CourseSpec course : dataset.courses()) {
            seedCourse(course);
        }
    }

    private List<Long> findInstructorIds() {
        List<Long> ids = new ArrayList<>();
        for (JsonNode instructor : session.api().get(DemoPersona.ADMIN, "/instructors")) {
            ids.add(instructor.get("id").asLong());
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("No hay docentes: el paso de personas debe correr antes");
        }
        return ids;
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

    private long findOrCreateAnnualTerm(int year) {
        for (JsonNode term : session.api().get(DemoPersona.ADMIN, "/terms")) {
            if (term.path("year").asInt() == year && "ANNUAL".equals(term.path("type").asString(""))
                    && "ACTIVE".equals(term.path("status").asString(""))) {
                return term.get("id").asLong();
            }
        }
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

    private void seedProgram(DemoCatalog.ProgramSpec spec) {
        if (session.findIdByName(session.api().get(DemoPersona.ADMIN, "/programs"), spec.name()) != null) {
            throw new IllegalStateException("La carrera " + spec.name() + " ya existe sin el marcador de la demo");
        }
        int subjectCount = spec.subjectsByYear().stream().mapToInt(List::size).sum();
        Map<String, Object> program = new LinkedHashMap<>();
        program.put("name", spec.name());
        program.put("totalYears", spec.totalYears());
        program.put("totalCreditsRequired", subjectCount * CREDITS_PER_SUBJECT);
        long programId = session.api().post(DemoPersona.ADMIN, "/programs", program).get("id").asLong();
        session.programIdsByName().put(spec.name(), programId);
        long termId = findOrCreateAnnualTerm(2026);
        Map<String, Long> programSubjectIdsByName = new LinkedHashMap<>();
        for (int yearIndex = 0; yearIndex < spec.subjectsByYear().size(); yearIndex++) {
            List<String> subjects = spec.subjectsByYear().get(yearIndex);
            for (int position = 0; position < subjects.size(); position++) {
                long subjectId = findOrCreateSubject(subjects.get(position));
                programSubjectIdsByName.put(subjects.get(position), linkSubject(programId, subjectId, yearIndex + 1, position));
                long sectionId = ensureSection(subjectId, termId);
                if (yearIndex == 0) {
                    session.firstYearSectionIdsByProgram().computeIfAbsent(spec.name(), name -> new ArrayList<>()).add(sectionId);
                }
            }
        }
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

    private long ensureSection(long subjectId, long termId) {
        Long existing = sectionIdsBySubjectId.get(subjectId);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("name", "Comisión A");
        section.put("termId", termId);
        section.put("schedule", SCHEDULES[(int) (subjectId % SCHEDULES.length)]);
        section.put("capacity", 30);
        section.put("shift", "MORNING");
        section.put("evaluationSchemeId", schemeId);
        long sectionId = session.api().post(DemoPersona.ADMIN, "/subjects/" + subjectId + "/sections", section).get("id").asLong();
        session.api().post(DemoPersona.ADMIN, "/program-sections/" + sectionId + "/instructors",
                Map.of("instructorId", nextLeadTeacher(), "role", "LEAD"));
        sectionIdsBySubjectId.put(subjectId, sectionId);
        return sectionId;
    }

    private long nextLeadTeacher() {
        return instructorIds.get(leadTeacherCursor++ % instructorIds.size());
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
        long courseId = session.api().post(DemoPersona.ADMIN, "/courses", course).get("id").asLong();
        session.courseIdsByName().put(spec.name(), courseId);
        int order = 1;
        for (String module : spec.subjects()) {
            long moduleId = session.api().post(DemoPersona.ADMIN, "/courses/" + courseId + "/subjects",
                    Map.of("name", module, "sortOrder", order++)).get("id").asLong();
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("name", "Comisión única");
            section.put("termId", termId);
            section.put("capacity", spec.capacity());
            section.put("shift", "MORNING");
            section.put("evaluationSchemeId", schemeId);
            long sectionId = session.api().post(DemoPersona.ADMIN, "/course-subjects/" + moduleId + "/sections", section).get("id").asLong();
            session.api().post(DemoPersona.ADMIN, "/course-sections/" + sectionId + "/instructors",
                    Map.of("instructorId", nextLeadTeacher(), "role", "LEAD"));
        }
    }
}

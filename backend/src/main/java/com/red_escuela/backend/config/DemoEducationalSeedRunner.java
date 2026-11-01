package com.red_escuela.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.config.infrastructure.DemoApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("local & demo")
@Order(DemoEducationalSeedRunner.RUN_ORDER)
public class DemoEducationalSeedRunner implements ApplicationRunner {
    public static final int RUN_ORDER = 20;
    public static final String PROGRAM_NAME = "Tecnicatura en Gestión Cultural (Demo)";
    public static final String GRADUATING_PROGRAM_NAME = "Tecnicatura en Producción Escénica (Demo)";
    public static final String COURSE_NAME = "Taller de Fotografía Digital (Demo)";
    public static final String TERM_DESCRIPTION = "Ciclo lectivo 2026 (Demo)";

    private static final Logger log = LoggerFactory.getLogger(DemoEducationalSeedRunner.class);
    private static final String ADMIN = "administrativo.demo";
    private static final String DIRECTOR = "director.demo";
    private static final String TEACHER = "docente.demo";
    private static final String TEACHER_FULL_NAME = "Docente Demo";
    private static final String REGULAR_STUDENT = "alumno.demo";
    private static final String GRADUATING_STUDENT = "alumno.egreso.demo";
    private static final String COURSE_STUDENT = "alumno.cursos.demo";
    private static final List<String[]> SUBJECTS = List.of(
            new String[] {"Introducción a la Gestión Cultural", "DM01"},
            new String[] {"Políticas Culturales", "DM02"},
            new String[] {"Producción de Eventos", "DM03"});
    private static final String[] GRADUATING_SUBJECT = {"Taller de Dirección Escénica", "DM04"};
    private static final List<String> COURSE_MODULES = List.of("Fundamentos de la imagen", "Composición y luz", "Edición digital");
    private static final List<String> CLASS_DATES = List.of("2026-03-09", "2026-03-11", "2026-03-16", "2026-03-18");

    private static final byte[] MEDICAL_CERTIFICATE_PDF =
            "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final Environment environment;

    public DemoEducationalSeedRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedThroughPort(environment.getRequiredProperty("local.server.port", Integer.class));
        } catch (RuntimeException failure) {
            log.error("Seed demo educativo incompleto: la base queda a medias, recrearla para reintentar", failure);
        }
    }

    public void seedThroughPort(int port) {
        DemoApiClient api = new DemoApiClient(port);
        if (alreadySeeded(api)) {
            return;
        }
        long programId = createProgram(api, PROGRAM_NAME, 15);
        List<Long> subjectIds = createActiveSubjects(api, programId);
        long schemeId = createEvaluationScheme(api);
        long termId = createOpenTerm(api);
        long instructorId = findTeacherInstructorId(api);
        List<Long> sectionIds = createSectionsWithLeadTeacher(api, subjectIds, termId, schemeId, instructorId);
        long regularStudentId = findStudentId(api, REGULAR_STUDENT);
        enrollRegularStudent(api, regularStudentId, programId, sectionIds);
        enrollGraduatingStudent(api, termId, schemeId, instructorId);
        seedLooseCourse(api, termId, schemeId, instructorId);
        log.info("Seed demo educativo creado: carrera={}, ciclo={}, curso={}", PROGRAM_NAME, TERM_DESCRIPTION, COURSE_NAME);
    }

    private boolean alreadySeeded(DemoApiClient api) {
        for (JsonNode program : api.get(ADMIN, "/programs")) {
            if (PROGRAM_NAME.equals(program.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private long createProgram(DemoApiClient api, String name, int totalCredits) {
        return api.post(ADMIN, "/programs", Map.of("name", name, "totalYears", 3, "totalCreditsRequired", totalCredits)).get("id").asLong();
    }

    private List<Long> createActiveSubjects(DemoApiClient api, long programId) {
        List<Long> subjectIds = new ArrayList<>();
        for (String[] subject : SUBJECTS) {
            subjectIds.add(createActiveSubjectInProgram(api, programId, subject));
        }
        return subjectIds;
    }

    private long createActiveSubjectInProgram(DemoApiClient api, long programId, String[] subject) {
        long subjectId = api.post(ADMIN, "/subjects", subjectDraft(subject)).get("id").asLong();
        api.post(ADMIN, "/subjects/" + subjectId + "/submit-for-approval", null);
        api.post(DIRECTOR, "/subjects/" + subjectId + "/approve", null);
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("subjectId", subjectId);
        link.put("category", "MANDATORY");
        link.put("curricularYear", 1);
        link.put("curricularTerm", "FIRST");
        link.put("credits", 5);
        api.post(ADMIN, "/programs/" + programId + "/subjects", link);
        return subjectId;
    }

    private Map<String, Object> subjectDraft(String[] subject) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("name", subject[0]);
        draft.put("code", subject[1]);
        draft.put("weeklyHours", 4);
        draft.put("gradeMode", "NUMERIC");
        return draft;
    }

    private long createEvaluationScheme(DemoApiClient api) {
        Map<String, Object> scheme = new LinkedHashMap<>();
        scheme.put("name", "Esquema demo: 2 parciales y final");
        scheme.put("examCountExpected", 2);
        scheme.put("hasFinal", true);
        scheme.put("finalWeight", 0.5);
        scheme.put("aggregationMethod", "SIMPLE_AVERAGE");
        scheme.put("minRegularGrade", 4);
        scheme.put("minApprovalGrade", 6);
        return api.post(DIRECTOR, "/evaluation-schemes", scheme).get("id").asLong();
    }

    private long createOpenTerm(DemoApiClient api) {
        Map<String, Object> term = new LinkedHashMap<>();
        term.put("year", 2026);
        term.put("description", TERM_DESCRIPTION);
        term.put("type", "ANUAL");
        term.put("startDate", "2026-03-01T00:00:00");
        term.put("endDate", "2026-12-20T00:00:00");
        long termId = api.post(ADMIN, "/terms", term).get("id").asLong();
        api.post(ADMIN, "/terms/" + termId + "/enrollment/open", null);
        return termId;
    }

    private long findTeacherInstructorId(DemoApiClient api) {
        for (JsonNode instructor : api.get(ADMIN, "/instructors")) {
            if (TEACHER_FULL_NAME.equals(instructor.path("name").asText())) {
                return instructor.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe el perfil de docente para " + TEACHER);
    }

    private List<Long> createSectionsWithLeadTeacher(DemoApiClient api, List<Long> subjectIds, long termId, long schemeId, long instructorId) {
        List<Long> sectionIds = new ArrayList<>();
        String[] schedules = {"Lunes y miércoles 18:00 a 20:00", "Martes y jueves 18:00 a 20:00", "Viernes 18:00 a 22:00"};
        for (int index = 0; index < subjectIds.size(); index++) {
            sectionIds.add(createSectionWithLeadTeacher(api, subjectIds.get(index), termId, schemeId, instructorId, "Comisión A", schedules[index]));
        }
        return sectionIds;
    }

    private long createSectionWithLeadTeacher(DemoApiClient api, long subjectId, long termId, long schemeId, long instructorId, String name, String schedule) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("name", name);
        section.put("termId", termId);
        section.put("schedule", schedule);
        section.put("capacity", 30);
        section.put("evaluationSchemeId", schemeId);
        long sectionId = api.post(ADMIN, "/subjects/" + subjectId + "/sections", section).get("id").asLong();
        api.post(ADMIN, "/program-sections/" + sectionId + "/instructors", Map.of("instructorId", instructorId, "role", "LEAD"));
        return sectionId;
    }

    private long findStudentId(DemoApiClient api, String username) {
        for (JsonNode student : api.get(ADMIN, "/students?search=" + username)) {
            if (student.toString().contains(username)) {
                return student.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe el alumno " + username);
    }

    private void enrollRegularStudent(DemoApiClient api, long studentId, long programId, List<Long> sectionIds) {
        long programEnrollmentId = api.post(ADMIN, "/students/" + studentId + "/program-enrollments", Map.of("programId", programId)).get("id").asLong();
        List<Long> sectionEnrollmentIds = new ArrayList<>();
        for (Long sectionId : sectionIds.subList(0, 2)) {
            sectionEnrollmentIds.add(api.post(ADMIN, "/program-enrollments/" + programEnrollmentId + "/section-enrollments", Map.of("sectionId", sectionId)).get("id").asLong());
        }
        for (int index = 0; index < sectionEnrollmentIds.size(); index++) {
            recordAttendance(api, sectionIds.get(index), sectionEnrollmentIds.get(index));
        }
        api.post(TEACHER, "/section-enrollments/" + sectionEnrollmentIds.get(0) + "/grade-items", Map.of("type", "EXAM", "date", "2026-04-15T00:00:00", "score", 7));
        justifyFirstAbsence(api, sectionEnrollmentIds.get(0));
    }

    private void enrollGraduatingStudent(DemoApiClient api, long termId, long schemeId, long instructorId) {
        long programId = createProgram(api, GRADUATING_PROGRAM_NAME, 5);
        long subjectId = createActiveSubjectInProgram(api, programId, GRADUATING_SUBJECT);
        long sectionId = createSectionWithLeadTeacher(api, subjectId, termId, schemeId, instructorId, "Comisión A", "Jueves 18:00 a 22:00");
        long studentId = findStudentId(api, GRADUATING_STUDENT);
        long programEnrollmentId = api.post(ADMIN, "/students/" + studentId + "/program-enrollments", Map.of("programId", programId)).get("id").asLong();
        long sectionEnrollmentId = api.post(ADMIN, "/program-enrollments/" + programEnrollmentId + "/section-enrollments", Map.of("sectionId", sectionId)).get("id").asLong();
        loadApprovingGrades(api, "/section-enrollments/" + sectionEnrollmentId + "/grade-items");
    }

    private void loadApprovingGrades(DemoApiClient api, String gradeItemsPath) {
        api.post(TEACHER, gradeItemsPath, Map.of("type", "EXAM", "sequenceNumber", 1, "date", "2026-06-10T00:00:00", "score", 7));
        api.post(TEACHER, gradeItemsPath, Map.of("type", "EXAM", "sequenceNumber", 2, "date", "2026-08-12T00:00:00", "score", 8));
        api.post(TEACHER, gradeItemsPath, Map.of("type", "FINAL", "date", "2026-09-20T00:00:00", "score", 8));
    }

    private void recordAttendance(DemoApiClient api, long sectionId, long sectionEnrollmentId) {
        for (int index = 0; index < CLASS_DATES.size(); index++) {
            String status = index == 1 || index == 3 ? "ABSENT" : "PRESENT";
            Map<String, Object> entry = Map.of("enrollmentId", sectionEnrollmentId, "status", status);
            api.post(TEACHER, "/program-sections/" + sectionId + "/attendance", Map.of("classDate", CLASS_DATES.get(index), "entries", List.of(entry)));
        }
    }

    private void justifyFirstAbsence(DemoApiClient api, long sectionEnrollmentId) {
        for (JsonNode record : api.get(REGULAR_STUDENT, "/me/section-enrollments/" + sectionEnrollmentId + "/attendance")) {
            if ("ABSENT".equals(record.path("status").asText())) {
                api.postFile(REGULAR_STUDENT, "/me/attendance/" + record.get("id").asLong() + "/justify",
                        "Certificado médico", "certificado.pdf", MEDICAL_CERTIFICATE_PDF);
                return;
            }
        }
    }

    private void seedLooseCourse(DemoApiClient api, long termId, long schemeId, long instructorId) {
        Map<String, Object> course = new LinkedHashMap<>();
        course.put("name", COURSE_NAME);
        course.put("termId", termId);
        course.put("schedule", "Sábados 10:00 a 13:00");
        course.put("capacity", 20);
        course.put("price", Map.of("amount", "15000", "currency", "ARS"));
        course.put("durationMonths", 3);
        long courseId = api.post(ADMIN, "/courses", course).get("id").asLong();
        List<Long> moduleSectionIds = new ArrayList<>();
        int order = 1;
        for (String module : COURSE_MODULES) {
            long moduleId = api.post(ADMIN, "/courses/" + courseId + "/subjects", Map.of("name", module, "sortOrder", order++)).get("id").asLong();
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("name", "Comisión única");
            section.put("termId", termId);
            section.put("capacity", 20);
            section.put("evaluationSchemeId", schemeId);
            long sectionId = api.post(ADMIN, "/course-subjects/" + moduleId + "/sections", section).get("id").asLong();
            api.post(ADMIN, "/course-sections/" + sectionId + "/instructors", Map.of("instructorId", instructorId, "role", "LEAD"));
            moduleSectionIds.add(sectionId);
        }
        api.post(ADMIN, "/students/" + findStudentId(api, COURSE_STUDENT) + "/course-enrollments", Map.of("courseId", courseId));
        for (Long sectionId : moduleSectionIds.subList(0, 2)) {
            long sectionEnrollmentId = api.get(TEACHER, "/course-sections/" + sectionId + "/roster").get(0).get("enrollmentId").asLong();
            loadApprovingGrades(api, "/course-section-enrollments/" + sectionEnrollmentId + "/grade-items");
        }
    }
}

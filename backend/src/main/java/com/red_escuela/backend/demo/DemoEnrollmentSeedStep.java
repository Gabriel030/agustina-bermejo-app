package com.red_escuela.backend.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoEnrollmentSeedStep implements DemoSeedStep {
    public static final int STEP_ORDER = 30;

    public static final int SECTIONS_PER_STUDENT = 3;
    private static final int COURSE_STUDENT_INTERVAL = 4;

    private final DemoSeedSession session;

    public DemoEnrollmentSeedStep(DemoSeedSession session) {
        this.session = session;
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
                Map.of("programId", programId)).get("id").asLong();
        List<Long> sectionIds = session.firstYearSectionIdsByProgram().get(program.name());
        for (Long sectionId : sectionIds.subList(0, Math.min(SECTIONS_PER_STUDENT, sectionIds.size()))) {
            session.api().post(DemoPersona.ADMIN, "/program-enrollments/" + programEnrollmentId + "/section-enrollments",
                    Map.of("sectionId", sectionId));
        }
    }

    private void enrollInCourse(long studentId, DemoCatalog.CourseSpec course) {
        session.api().post(DemoPersona.ADMIN, "/students/" + studentId + "/course-enrollments",
                Map.of("courseId", session.courseIdsByName().get(course.name())));
    }
}

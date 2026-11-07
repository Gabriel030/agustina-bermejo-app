package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradeCorrectionCharacterizationTest extends CourseworkCharacterizationSupport {

    private Classroom publishedExam(String key, String dniBase, String score) throws Exception {
        Classroom classroom = persistClassroom(key, dniBase, persistScheme(2, false, null), null, 10, 10, InstructorRole.LEAD);
        postJson(classroom.teacher(), "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal(score), null, null)).andExpect(status().isCreated());
        postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/grade-items/publish",
                new PublishGradeItemRequest(GradeItemType.EXAM, 1)).andExpect(status().isOk());
        return classroom;
    }

    private long gradeItemIdOf(Classroom classroom, Person director) throws Exception {
        return bodyOf(getAs(director, "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items")).get(0).get("id").asLong();
    }

    private Map<String, Object> correction(Object newScore, String reason) {
        return Map.of("newScore", newScore, "reason", reason);
    }

    @Test
    void directorCorrectsAPublishedGradeAndTheStudentIsNotifiedOncePerEvent() throws Exception {
        Classroom classroom = publishedExam("cor1", "7800001", "5");
        Person director = persistPerson("cor1.director", "78000017", "DIRECTOR");
        long gradeItemId = gradeItemIdOf(classroom, director);

        postJson(director, "/grade-items/" + gradeItemId + "/corrections", correction(8.5, "Error de transcripción del docente"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousScore").value(5.0))
                .andExpect(jsonPath("$.newScore").value(8.5))
                .andExpect(jsonPath("$.reason").value("Error de transcripción del docente"))
                .andExpect(jsonPath("$.changedById").value(director.getId()))
                .andExpect(jsonPath("$.kind").value("CORRECTION"));

        getAs(director, "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items")
                .andExpect(jsonPath("$[0].score").value(8.5));
        getAs(director, "/grade-items/" + gradeItemId + "/audit")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        getAs(classroom.student(), "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='GRADES_PUBLISHED')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.type=='GRADE_CORRECTED')]", hasSize(1)));
    }

    @Test
    void teacherCannotCorrectAPublishedGrade() throws Exception {
        Classroom classroom = publishedExam("cor2", "7800002", "5");
        Person director = persistPerson("cor2.director", "78000027", "DIRECTOR");
        long gradeItemId = gradeItemIdOf(classroom, director);

        postJson(classroom.teacher(), "/grade-items/" + gradeItemId + "/corrections", correction(9, "Quiero cambiar la nota"))
                .andExpect(status().isForbidden());
        getAs(classroom.teacher(), "/grade-items/" + gradeItemId + "/audit").andExpect(status().isForbidden());

        getAs(director, "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items")
                .andExpect(jsonPath("$[0].score").value(5.0));
    }

    @Test
    void directorCannotCorrectAfterTheTermEnded() throws Exception {
        Classroom classroom = publishedExam("cor3", "7800003", "5");
        Person director = persistPerson("cor3.director", "78000037", "DIRECTOR");
        long gradeItemId = gradeItemIdOf(classroom, director);
        MutableClock.INSTANCE.setDate(LocalDate.of(3000, 1, 1));

        postJson(director, "/grade-items/" + gradeItemId + "/corrections", correction(9, "Fuera de plazo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CORRECTION_PERIOD_ENDED"));
    }

    @Test
    void directorGetsABadRequestForAnInvalidReasonOrScore() throws Exception {
        Classroom classroom = publishedExam("cor4", "7800004", "5");
        Person director = persistPerson("cor4.director", "78000047", "DIRECTOR");
        long gradeItemId = gradeItemIdOf(classroom, director);
        String path = "/grade-items/" + gradeItemId + "/corrections";

        postJson(director, path, correction(9, "abcd")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRADE_CORRECTION_REASON"));
        postJson(director, path, correction(9, "a".repeat(501))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRADE_CORRECTION_REASON"));
        postJson(director, path, Map.of("newScore", 9)).andExpect(status().isBadRequest());
        postJson(director, path, correction(11, "Nota fuera de la escala")).andExpect(status().isBadRequest());

        getAs(director, "/grade-items/" + gradeItemId + "/audit").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sendingTheSameCorrectionTwiceLeavesASingleAuditRow() throws Exception {
        Classroom classroom = publishedExam("cor5", "7800005", "5");
        Person director = persistPerson("cor5.director", "78000057", "DIRECTOR");
        long gradeItemId = gradeItemIdOf(classroom, director);
        String path = "/grade-items/" + gradeItemId + "/corrections";

        postJson(director, path, correction(8, "Primer envío")).andExpect(status().isCreated());
        postJson(director, path, correction(8, "Reintento del mismo pedido")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CORRECTION_NO_CHANGE"));

        getAs(director, "/grade-items/" + gradeItemId + "/audit").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void staffGridMarksOnlyTheCorrectedPublishedCellAndExposesTheTermEndAsDeadline() throws Exception {
        Classroom classroom = publishedExam("cor6", "7800006", "5");
        Person director = persistPerson("cor6.director", "78000067", "DIRECTOR");
        classroom.section().getTerm().setEndDate(LocalDateTime.of(2026, 12, 31, 23, 59));
        entityManager.flush();
        long gradeItemId = gradeItemIdOf(classroom, director);
        String staffGrid = "/staff/program-sections/" + classroom.section().getId() + "/grade-grid";

        getAs(director, staffGrid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctionDeadline").value("2026-12-31"))
                .andExpect(jsonPath("$.students[0].grades[0].corrected").value(false));

        postJson(director, "/grade-items/" + gradeItemId + "/corrections", correction(8, "Error de carga")).andExpect(status().isCreated());

        getAs(director, staffGrid)
                .andExpect(jsonPath("$.students[0].grades[0].corrected").value(true))
                .andExpect(jsonPath("$.students[0].grades[0].score").value(8.0));
        getAs(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/grade-grid")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctionDeadline").value("2026-12-31"))
                .andExpect(jsonPath("$.students[0].grades[0].corrected").value(true));

        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 1, 1));
        postJson(director, "/grade-items/" + gradeItemId + "/corrections", correction(9, "Fuera de plazo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_CORRECTION_PERIOD_ENDED"));
        getAs(director, staffGrid)
                .andExpect(jsonPath("$.correctionDeadline").value("2026-12-31"))
                .andExpect(jsonPath("$.students[0].grades[0].corrected").value(true));
    }

    @Test
    void directorReadsTheGridWithoutTeachingWhileStudentsAndTeachersOfOtherSectionsStayOut() throws Exception {
        Classroom classroom = publishedExam("cor7", "7800007", "5");
        Person director = persistPerson("cor7.director", "78000077", "DIRECTOR");
        Person stranger = persistPerson("cor7.stranger", "78000079", "DOCENTE");
        persistInstructor(stranger);
        String staffGrid = "/staff/program-sections/" + classroom.section().getId() + "/grade-grid";
        String teacherGrid = "/program-sections/" + classroom.section().getId() + "/grade-grid";

        getAs(director, staffGrid).andExpect(status().isOk())
                .andExpect(jsonPath("$.students[0].enrollmentId").value(classroom.enrollment().getId()));
        getAs(director, teacherGrid).andExpect(status().isForbidden());
        getAs(stranger, teacherGrid).andExpect(status().isForbidden());
        getAs(stranger, staffGrid).andExpect(status().isForbidden());
        getAs(classroom.teacher(), staffGrid).andExpect(status().isForbidden());
        getAs(classroom.student(), staffGrid).andExpect(status().isForbidden());
        getAs(director, "/staff/program-sections/999999999/grade-grid").andExpect(status().isNotFound());
        getAs(director, "/staff/course-sections/999999999/grade-grid").andExpect(status().isNotFound());
    }

    @Test
    void studentSeesOnlyTheLastCorrectionWithItsReasonAndNothingWhenNeverCorrected() throws Exception {
        Classroom classroom = publishedExam("cor8", "7800008", "5");
        Person director = persistPerson("cor8.director", "78000087", "DIRECTOR");
        long gradeItemId = gradeItemIdOf(classroom, director);
        String studentGrades = "/me/section-enrollments/" + classroom.enrollment().getId() + "/grade-items";

        getAs(classroom.student(), studentGrades)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correction").doesNotExist());

        postJson(director, "/grade-items/" + gradeItemId + "/corrections", correction(8.5, "Primera correccion")).andExpect(status().isCreated());
        postJson(director, "/grade-items/" + gradeItemId + "/corrections", correction(7, "Segunda correccion")).andExpect(status().isCreated());

        getAs(classroom.student(), studentGrades)
                .andExpect(jsonPath("$[0].score").value(7.0))
                .andExpect(jsonPath("$[0].correction.previousScore").value(8.5))
                .andExpect(jsonPath("$[0].correction.reason").value("Segunda correccion"))
                .andExpect(jsonPath("$[0].correction.correctedAt").exists());
    }
}

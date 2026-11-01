package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.RejectAttendanceJustificationRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceJustificationStaffCharacterizationTest extends CourseworkCharacterizationSupport {

    private static final String LONG_REASON = "El comprobante no es legible";

    @Autowired
    private AttendanceProofSubmitter attendanceProofSubmitter;

    @Value("${app.uploads.directory}")
    private String uploadsDirectory;

    private Classroom classroom(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null), null, 10, 10, InstructorRole.LEAD);
    }

    private long pendingJustification(Classroom classroom, int day) throws Exception {
        CargarAsistenciaRequest request = new CargarAsistenciaRequest(
                LocalDate.of(2026, 3, day), List.of(new AttendanceEntry(classroom.enrollment().getId(), AttendanceStatus.ABSENT)), null, null);
        long attendanceId = bodyOf(postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance", request)
                .andExpect(status().isCreated())).get(0).get("id").asLong();
        attendanceProofSubmitter.submitProof(attendanceId, "Fiebre", "students/1/comprobante.pdf");
        entityManager.flush();
        return attendanceId;
    }

    private long totalOf(Person actor, String status) throws Exception {
        return bodyOf(getAs(actor, "/attendance/justifications?size=100&status=" + status).andExpect(status().isOk())).get("totalElements").asLong();
    }

    private String itemStatus(long attendanceId) {
        return "$.items[?(@.attendanceId==" + attendanceId + ")].status";
    }

    @Test
    void rejectingWithAReasonStoresItReturnsItListsItAsRejectedAndNotifiesTheStudentOnce() throws Exception {
        Classroom classroom = classroom("jus1", "7700001");
        Person preceptor = persistPerson("jus1.preceptor", "77000017", "PRECEPTOR");
        long attendanceId = pendingJustification(classroom, 2);

        postJson(preceptor, "/attendance/" + attendanceId + "/reject-justification", new RejectAttendanceJustificationRequest(LONG_REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABSENT"))
                .andExpect(jsonPath("$.justificationRejectionReason").value(LONG_REASON));

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance")
                .andExpect(jsonPath("$[?(@.id==" + attendanceId + ")].justificationRejectionReason", contains(LONG_REASON)));
        getAs(preceptor, "/attendance/justifications?size=100&status=REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath(itemStatus(attendanceId), contains("REJECTED")))
                .andExpect(jsonPath("$.items[?(@.attendanceId==" + attendanceId + ")].rejectionReason", contains(LONG_REASON)))
                .andExpect(jsonPath("$.items[?(@.attendanceId==" + attendanceId + ")].reason", contains("Fiebre")));
        getAs(preceptor, "/attendance/justifications?size=100")
                .andExpect(jsonPath(itemStatus(attendanceId), hasSize(0)));
        getAs(classroom.student(), "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='ATTENDANCE_JUSTIFICATION_REJECTED')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.type=='ATTENDANCE_JUSTIFICATION_APPROVED')]", hasSize(0)));
    }

    @Test
    void approvingListsItAsApprovedAndNotifiesTheStudentOnce() throws Exception {
        Classroom classroom = classroom("jus2", "7700002");
        Person administrative = persistPerson("jus2.admin", "77000027", "ADMINISTRATIVO");
        long attendanceId = pendingJustification(classroom, 2);
        getAs(administrative, "/attendance/justifications?size=100")
                .andExpect(jsonPath(itemStatus(attendanceId), contains("PENDING")));

        postEmpty(administrative, "/attendance/" + attendanceId + "/approve-justification")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JUSTIFIED"));

        getAs(administrative, "/attendance/justifications?size=100&status=APPROVED")
                .andExpect(jsonPath(itemStatus(attendanceId), contains("APPROVED")));
        getAs(classroom.student(), "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='ATTENDANCE_JUSTIFICATION_APPROVED')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.type=='ATTENDANCE_JUSTIFICATION_REJECTED')]", hasSize(0)));
    }

    @Test
    void aTooShortOrMissingReasonIsRejectedWithoutChangingTheJustification() throws Exception {
        Classroom classroom = classroom("jus3", "7700003");
        Person preceptor = persistPerson("jus3.preceptor", "77000037", "PRECEPTOR");
        long attendanceId = pendingJustification(classroom, 2);
        String path = "/attendance/" + attendanceId + "/reject-justification";

        postJson(preceptor, path, new RejectAttendanceJustificationRequest("corto")).andExpect(status().isBadRequest());
        postJson(preceptor, path, new RejectAttendanceJustificationRequest("   ")).andExpect(status().isBadRequest());
        postEmpty(preceptor, path).andExpect(status().isBadRequest());

        getAs(preceptor, "/attendance/justifications?size=100")
                .andExpect(jsonPath(itemStatus(attendanceId), contains("PENDING")));
    }

    @Test
    void decidingAJustificationThatIsNotPendingOrDoesNotExistFails() throws Exception {
        Classroom classroom = classroom("jus4", "7700004");
        Person preceptor = persistPerson("jus4.preceptor", "77000047", "PRECEPTOR");
        long attendanceId = pendingJustification(classroom, 2);
        postJson(preceptor, "/attendance/" + attendanceId + "/reject-justification", new RejectAttendanceJustificationRequest(LONG_REASON))
                .andExpect(status().isOk());

        postJson(preceptor, "/attendance/" + attendanceId + "/reject-justification", new RejectAttendanceJustificationRequest(LONG_REASON))
                .andExpect(status().isConflict());
        postEmpty(preceptor, "/attendance/" + attendanceId + "/approve-justification").andExpect(status().isConflict());
        postJson(preceptor, "/attendance/999999/reject-justification", new RejectAttendanceJustificationRequest(LONG_REASON))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyPreceptorAndAdministrativeCanListAndRejectJustifications() throws Exception {
        Classroom classroom = classroom("jus5", "7700005");
        long attendanceId = pendingJustification(classroom, 2);
        String rejectPath = "/attendance/" + attendanceId + "/reject-justification";

        for (Person outsider : List.of(classroom.student(), classroom.teacher())) {
            getAs(outsider, "/attendance/justifications").andExpect(status().isForbidden());
            postJson(outsider, rejectPath, new RejectAttendanceJustificationRequest(LONG_REASON)).andExpect(status().isForbidden());
        }
    }

    @Test
    void listingIsPaginatedAndResubmittingAfterARejectionClearsTheRejection() throws Exception {
        Classroom classroom = classroom("jus6", "7700006");
        Person preceptor = persistPerson("jus6.preceptor", "77000067", "PRECEPTOR");
        long pendingBefore = totalOf(preceptor, "PENDING");
        long rejectedBefore = totalOf(preceptor, "REJECTED");
        long first = pendingJustification(classroom, 2);
        pendingJustification(classroom, 3);
        pendingJustification(classroom, 4);

        assertThat(totalOf(preceptor, "PENDING")).isEqualTo(pendingBefore + 3);
        getAs(preceptor, "/attendance/justifications?page=0&size=2")
                .andExpect(jsonPath("$.items", hasSize(2)));
        getAs(preceptor, "/attendance/justifications?page=" + (pendingBefore + 2) / 2 + "&size=2")
                .andExpect(jsonPath("$.items", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        postJson(preceptor, "/attendance/" + first + "/reject-justification", new RejectAttendanceJustificationRequest(LONG_REASON))
                .andExpect(status().isOk());
        attendanceProofSubmitter.submitProof(first, "Nuevo comprobante", "students/1/otro.pdf");
        entityManager.flush();

        assertThat(entityManager.find(Attendance.class, first).getJustificationRejectionReason()).isNull();
        assertThat(entityManager.find(Attendance.class, first).getJustificationRejectedAt()).isNull();
        assertThat(totalOf(preceptor, "REJECTED")).isEqualTo(rejectedBefore);
        assertThat(totalOf(preceptor, "PENDING")).isEqualTo(pendingBefore + 3);
    }

    @Test
    void staffOpensTheAttachmentOfAJustificationAndOthersAreDenied() throws Exception {
        Classroom classroom = classroom("jus7", "7700007");
        Person preceptor = persistPerson("jus7.preceptor", "77000077", "PRECEPTOR");
        Path file = Path.of(uploadsDirectory).resolve("students/7/jus7-comprobante.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        CargarAsistenciaRequest request = new CargarAsistenciaRequest(
                LocalDate.of(2026, 3, 2), List.of(new AttendanceEntry(classroom.enrollment().getId(), AttendanceStatus.ABSENT)), null, null);
        long attendanceId = bodyOf(postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance", request)
                .andExpect(status().isCreated())).get(0).get("id").asLong();
        attendanceProofSubmitter.submitProof(attendanceId, "Fiebre", "students/7/jus7-comprobante.pdf");
        entityManager.flush();
        String path = "/attendance/" + attendanceId + "/justification-attachment";

        getAs(preceptor, path)
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[] {1, 2, 3}));
        getAs(classroom.student(), path).andExpect(status().isForbidden());
        getAs(classroom.teacher(), path).andExpect(status().isForbidden());
    }

    @Test
    void openingTheAttachmentFailsWhenThereIsNoFileOrNoAttendance() throws Exception {
        Classroom classroom = classroom("jus8", "7700008");
        Person preceptor = persistPerson("jus8.preceptor", "77000087", "PRECEPTOR");
        long attendanceId = pendingJustification(classroom, 2);

        getAs(preceptor, "/attendance/" + attendanceId + "/justification-attachment").andExpect(status().isNotFound());
        getAs(preceptor, "/attendance/999999/justification-attachment").andExpect(status().isNotFound());
    }

    @Test
    void justifyingARejectedAbsenceClearsThePreviousRejection() throws Exception {
        Classroom classroom = classroom("jus9", "7700009");
        Person preceptor = persistPerson("jus9.preceptor", "77000097", "PRECEPTOR");
        long attendanceId = pendingJustification(classroom, 2);
        postJson(preceptor, "/attendance/" + attendanceId + "/reject-justification", new RejectAttendanceJustificationRequest(LONG_REASON))
                .andExpect(status().isOk());

        postJson(preceptor, "/attendance/" + attendanceId + "/justify",
                new com.red_escuela.backend.attendance.dto.JustificarAsistenciaRequest("Certificado presentado en mostrador", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JUSTIFIED"))
                .andExpect(jsonPath("$.justificationRejectionReason").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(Attendance.class, attendanceId).getJustificationRejectionReason()).isNull();
        assertThat(entityManager.find(Attendance.class, attendanceId).getJustificationRejectedAt()).isNull();
    }
}

package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnouncementStaffReadReceiptsCharacterizationTest extends CourseworkCharacterizationSupport {

    private Classroom classroomWithSentAnnouncement(String key, String dniBase, boolean read) {
        Classroom classroom = persistClassroom(key, dniBase, persistScheme(2, true, null), null, 10, 10, InstructorRole.LEAD);
        Announcement announcement = Announcement.builder()
                .subjectSection(classroom.section())
                .createdBy(classroom.teacher())
                .title("Aviso " + key)
                .body("Cuerpo")
                .category(AnnouncementCategory.REMINDER)
                .audienceSpec(AudienceSpec.wholeSection())
                .audienceLabel("Toda la comisión")
                .status(AnnouncementStatus.SENT)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(announcement);
        entityManager.persist(AnnouncementRecipient.builder()
                .announcement(announcement)
                .student(classroom.enrollment().getStudent())
                .readAt(read ? LocalDateTime.now() : null)
                .build());
        entityManager.flush();
        return classroom;
    }

    @Test
    void preceptorListsSectionAnnouncementsWithReadCounts() throws Exception {
        Classroom classroom = classroomWithSentAnnouncement("rr1", "7810001", true);
        Person preceptor = persistPerson("rr1.preceptor", "78100017", "PRECEPTOR");

        getAs(preceptor, "/subject-sections/" + classroom.section().getId() + "/announcements/read-receipts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Aviso rr1"))
                .andExpect(jsonPath("$[0].recipientCount").value(1))
                .andExpect(jsonPath("$[0].readCount").value(1));
    }

    @Test
    void directorListsSectionAnnouncementsWithUnreadCounts() throws Exception {
        Classroom classroom = classroomWithSentAnnouncement("rr2", "7810002", false);
        Person director = persistPerson("rr2.director", "78100027", "DIRECTOR");

        getAs(director, "/subject-sections/" + classroom.section().getId() + "/announcements/read-receipts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientCount").value(1))
                .andExpect(jsonPath("$[0].readCount").value(0));
    }

    @Test
    void teacherAndAdministrativeAreForbiddenFromTheStaffListing() throws Exception {
        Classroom classroom = classroomWithSentAnnouncement("rr3", "7810003", true);
        Person administrative = persistPerson("rr3.admin", "78100037", "ADMINISTRATIVO");
        String path = "/subject-sections/" + classroom.section().getId() + "/announcements/read-receipts";

        getAs(classroom.teacher(), path).andExpect(status().isForbidden());
        getAs(administrative, path).andExpect(status().isForbidden());
        getAs(classroom.student(), path).andExpect(status().isForbidden());
    }

    @Test
    void unknownSectionsAreNotFoundForBothSectionKinds() throws Exception {
        Person preceptor = persistPerson("rr4.preceptor", "78100047", "PRECEPTOR");

        getAs(preceptor, "/subject-sections/999999999/announcements/read-receipts").andExpect(status().isNotFound());
        getAs(preceptor, "/course-sections/999999999/announcements/read-receipts").andExpect(status().isNotFound());
    }
}

package com.red_escuela.backend.announcement;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.service.AnnouncementReadCounter;
import com.red_escuela.backend.announcement.service.AnnouncementReadCounter.ReadCount;
import com.red_escuela.backend.announcement.service.AnnouncementReadReceiptReader;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.service.AnnouncementReadRecorder;
import com.red_escuela.backend.announcement.service.AnnouncementRecipientReader;
import com.red_escuela.backend.announcement.service.AnnouncementRenotifier;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AnnouncementReadReceiptIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AnnouncementReadRecorder readRecorder;
    @Autowired
    private AnnouncementReadCounter readCounter;
    @Autowired
    private AnnouncementRenotifier renotifier;
    @Autowired
    private AnnouncementReadReceiptReader receiptReader;
    @Autowired
    private AnnouncementRecipientRepository recipientRepository;

    private Long sentId;
    private Long scheduledId;
    private Long readerId;
    private Long otherRecipientId;
    private Long strangerId;

    private Student student(CycleTestFixtures fixtures, Institution institution) {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Announcement announcement(CycleTestFixtures fixtures, SubjectSection section, AnnouncementStatus status) {
        Announcement announcement = Announcement.builder()
                .subjectSection(section)
                .createdBy(fixtures.person())
                .title("Aviso")
                .body("Cuerpo")
                .category(AnnouncementCategory.REMINDER)
                .audienceSpec(AudienceSpec.wholeSection())
                .audienceLabel("Toda la comisión")
                .status(status)
                .sentAt(status == AnnouncementStatus.SENT ? LocalDateTime.now() : null)
                .scheduledFor(status == AnnouncementStatus.SCHEDULED ? LocalDateTime.now().plusDays(1) : null)
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(announcement);
        return announcement;
    }

    @BeforeEach
    void seedSentAndScheduledAnnouncementsWithTwoRecipients() {
        transactionTemplate.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            var term = fixtures.term(2100 + (int) (System.nanoTime() % 800));
            var cycle = fixtures.programCycle(fixtures.program(), term,
                    AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term,
                    StatusType.ACTIVE, cycle);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student reader = student(fixtures, institution);
            Student other = student(fixtures, institution);
            Student stranger = student(fixtures, institution);
            Announcement sent = announcement(fixtures, section, AnnouncementStatus.SENT);
            Announcement scheduled = announcement(fixtures, section, AnnouncementStatus.SCHEDULED);
            for (Announcement announcement : List.of(sent, scheduled)) {
                for (Student recipient : List.of(reader, other)) {
                    entityManager.persist(AnnouncementRecipient.builder().announcement(announcement).student(recipient).build());
                }
            }
            entityManager.flush();
            sentId = sent.getId();
            scheduledId = scheduled.getId();
            readerId = reader.getId();
            otherRecipientId = other.getId();
            strangerId = stranger.getId();
        });
    }

    private LocalDateTime readAtOf(Long announcementId, Long studentId) {
        return jdbcTemplate.queryForObject(
                "select read_at from announcement_recipient where announcement_id = ? and student_id = ?",
                LocalDateTime.class, announcementId, studentId);
    }

    @Test
    void recordsTheReadOnlyForTheStudentWhoOpenedTheAnnouncement() {
        readRecorder.recordRead(sentId, readerId);

        assertThat(readAtOf(sentId, readerId)).isNotNull();
        assertThat(readAtOf(sentId, otherRecipientId)).isNull();
    }

    @Test
    void keepsTheFirstReadTimeWhenTheStudentOpensTheAnnouncementAgain() {
        jdbcTemplate.update("update announcement_recipient set read_at = ? where announcement_id = ? and student_id = ?",
                LocalDateTime.of(2020, 1, 1, 8, 0), sentId, readerId);

        readRecorder.recordRead(sentId, readerId);

        assertThat(readAtOf(sentId, readerId)).isEqualTo(LocalDateTime.of(2020, 1, 1, 8, 0));
    }

    @Test
    void answersNotFoundWhenTheStudentIsNotARecipient() {
        assertThatThrownBy(() -> readRecorder.recordRead(sentId, strangerId))
                .isInstanceOf(AnnouncementNotFoundException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from announcement_recipient where announcement_id = ? and read_at is not null",
                Long.class, sentId)).isZero();
    }

    @Test
    void ignoresReadsOfAnAnnouncementThatWasNotSentYet() {
        readRecorder.recordRead(scheduledId, readerId);

        assertThat(readAtOf(scheduledId, readerId)).isNull();
    }

    @Test
    void countsRecipientsAndReadsPerAnnouncement() {
        readRecorder.recordRead(sentId, readerId);

        assertThat(readCounter.countsOf(List.of(sentId, scheduledId)))
                .containsEntry(sentId, new ReadCount(2, 1))
                .containsEntry(scheduledId, new ReadCount(2, 0));
    }

    @Test
    void keepsReadAtWhenTheUpdatedAnnouncementIsRenotified() {
        readRecorder.recordRead(sentId, readerId);
        LocalDateTime firstRead = readAtOf(sentId, readerId);

        transactionTemplate.executeWithoutResult(status ->
                renotifier.renotifyFrozenRecipients(entityManager.find(Announcement.class, sentId)));

        assertThat(readAtOf(sentId, readerId)).isEqualTo(firstRead);
    }

    @Test
    void listsTheReadTimeOfEachRecipientForStaff() {
        readRecorder.recordRead(sentId, readerId);

        assertThat(receiptReader.listReceipts(sentId))
                .extracting(AnnouncementRecipientReader.RecipientSummary::studentId, AnnouncementRecipientReader.RecipientSummary::readAt)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(readerId, readAtOf(sentId, readerId)),
                        org.assertj.core.groups.Tuple.tuple(otherRecipientId, null));
    }

    @Test
    void answersNotFoundWhenStaffAsksForAnAnnouncementThatDoesNotExist() {
        assertThatThrownBy(() -> receiptReader.listReceipts(-1L)).isInstanceOf(AnnouncementNotFoundException.class);
    }

    @Test
    void findsOnlyTheReadTimesOfTheGivenStudent() {
        readRecorder.recordRead(sentId, readerId);

        assertThat(recipientRepository.findReadTimes(readerId, List.of(sentId, scheduledId)))
                .extracting(AnnouncementRecipientRepository.ReadTime::getAnnouncementId)
                .containsExactly(sentId);
        assertThat(recipientRepository.findReadTimes(otherRecipientId, List.of(sentId, scheduledId))).isEmpty();
    }

    @Test
    void announcementsPersistedByTheApplicationAreTrackedForReads() {
        assertThat(jdbcTemplate.queryForObject("select read_tracked from announcement where id = ?", Boolean.class, sentId)).isTrue();
    }
}

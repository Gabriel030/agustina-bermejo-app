package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommissionClosureReminderRepositoryIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 13, 8, 30);

    @Autowired
    private CommissionClosureReminderRepository reminderRepository;
    @Autowired
    private SectionGradeItemPublicationRepository publicationRepository;

    @Test
    void aReminderIsRecordedOncePerSectionAndThresholdAndARepeatIsIgnored() {
        assertThat(reminderRepository.insertIfAbsent(SectionKind.PROGRAM, 990001L, 3, NOW)).isEqualTo(1);
        assertThat(reminderRepository.insertIfAbsent(SectionKind.PROGRAM, 990001L, 3, NOW.plusHours(1))).isZero();
        assertThat(reminderRepository.insertIfAbsent(SectionKind.PROGRAM, 990001L, 7, NOW.plusDays(4))).isEqualTo(1);
        assertThat(reminderRepository.insertIfAbsent(SectionKind.COURSE, 990001L, 3, NOW)).isEqualTo(1);
        assertThat(reminderRepository.findAll()).filteredOn(reminder -> reminder.getSectionId().equals(990001L)).hasSize(3);
    }

    @Test
    void theDistinctPublishedSectionsListEachSectionOnceNoMatterHowManyColumnsItPublished() {
        publicationRepository.save(SectionGradeItemPublication.of(SectionKind.PROGRAM, 990002L, GradeItemType.EXAM, 1, NOW, null));
        publicationRepository.save(SectionGradeItemPublication.of(SectionKind.PROGRAM, 990002L, GradeItemType.EXAM, 2, NOW, null));
        publicationRepository.save(SectionGradeItemPublication.of(SectionKind.COURSE, 990002L, GradeItemType.EXAM, 1, NOW, null));

        assertThat(publicationRepository.findDistinctPublishedSections())
                .filteredOn(section -> section.sectionId().equals(990002L))
                .containsExactlyInAnyOrder(new PublishedSectionRef(SectionKind.PROGRAM, 990002L), new PublishedSectionRef(SectionKind.COURSE, 990002L));
    }
}

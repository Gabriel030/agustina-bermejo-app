package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudienceSampleEntry;
import com.red_escuela.backend.notification.broadcast.audience.dto.BroadcastAudiencePreviewResponse;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.usecase.PreviewBroadcastAudienceUseCase;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.PersonEnrollmentTestFixtures;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.specialist.PersonTemplateVariableSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AudiencePreviewCourseVariableIT extends AbstractIntegrationTest {

    private static final String BODY = "Tu carrera: {{carrera}} / {{materia}} / {{comision}}";
    private static final LocalDateTime ENROLLED_AT = LocalDateTime.of(2041, 3, 1, 10, 0);

    @Autowired
    private PreviewBroadcastAudienceUseCase previewBroadcastAudience;

    @Autowired
    private PersonTemplateVariableSource templateVariableSource;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private RoleRepository roleRepository;

    private int sequence;

    private Person preceptor() {
        return AudienceTestPersons.create(entityManager, roleRepository, "course-var-" + (sequence++), true, null, "PRECEPTOR");
    }

    private void enroll(PersonEnrollmentTestFixtures fixtures, Person person, EnrollmentStatus status) {
        fixtures.enroll(fixtures.studentOf(person), "Carrera " + person.getId(), "Materia " + person.getId(),
                "Comisión " + person.getId(), ENROLLED_AT, status);
    }

    private BroadcastAudiencePreviewResponse preview(boolean excludeMissing) {
        return previewBroadcastAudience.previewBroadcastAudience(new AudiencePreviewRequest(ChannelType.EMAIL,
                BroadcastCategory.INSTITUTIONAL,
                new SegmentSpecRequest(1, SegmentQuantifier.ALL, List.of(new SegmentCriterionRequest(SegmentKey.STAFF_ROLE, "PRECEPTOR"))),
                excludeMissing, null, "Aviso", BODY, 0, 100));
    }

    private static AudienceSampleEntry entryOf(BroadcastAudiencePreviewResponse response, Person person) {
        return response.sample().stream().filter(entry -> entry.personId().equals(person.getId())).findFirst().orElseThrow();
    }

    @Test
    void personWithCurrentEnrollmentIsNotExcludedByCourseVariables() {
        Person enrolled = preceptor();
        PersonEnrollmentTestFixtures fixtures = new PersonEnrollmentTestFixtures(entityManager);
        enroll(fixtures, enrolled, EnrollmentStatus.IN_PROGRESS);

        AudienceSampleEntry entry = entryOf(preview(true), enrolled);

        assertThat(entry.status()).isEqualTo("INCLUDED");
        assertThat(entry.exclusionReason()).isNull();
    }

    @Test
    void personWithoutCurrentEnrollmentIsExcludedAsMissingVariable() {
        Person withoutEnrollment = preceptor();
        Person withdrawn = preceptor();
        PersonEnrollmentTestFixtures fixtures = new PersonEnrollmentTestFixtures(entityManager);
        enroll(fixtures, withdrawn, EnrollmentStatus.WITHDRAWN);

        BroadcastAudiencePreviewResponse response = preview(true);

        assertThat(entryOf(response, withoutEnrollment).exclusionReason()).isEqualTo(ExclusionReason.MISSING_TEMPLATE_VARIABLE);
        assertThat(entryOf(response, withdrawn).exclusionReason()).isEqualTo(ExclusionReason.MISSING_TEMPLATE_VARIABLE);
    }

    @Test
    void previewAndConfirmationExcludeTheSamePeople() {
        PersonEnrollmentTestFixtures fixtures = new PersonEnrollmentTestFixtures(entityManager);
        for (EnrollmentStatus status : new EnrollmentStatus[]{EnrollmentStatus.ENROLLED, EnrollmentStatus.REGULAR,
                EnrollmentStatus.WITHDRAWN, null}) {
            Person person = preceptor();
            if (status != null) {
                enroll(fixtures, person, status);
            }
        }
        entityManager.clear();

        BroadcastAudiencePreviewResponse response = preview(true);
        Set<Long> excludedInPreview = response.sample().stream()
                .filter(entry -> "EXCLUDED".equals(entry.status()))
                .map(AudienceSampleEntry::personId).collect(Collectors.toSet());
        List<Long> everyone = response.sample().stream().map(AudienceSampleEntry::personId).toList();
        Map<Long, Map<TemplateVariable, String>> values = templateVariableSource.valuesForAll(everyone);
        Set<TemplateVariable> courseVariables = Set.of(TemplateVariable.PROGRAM, TemplateVariable.SUBJECT, TemplateVariable.SECTION);
        Set<Long> unresolvedInConfirmation = everyone.stream()
                .filter(id -> !values.get(id).keySet().containsAll(courseVariables))
                .collect(Collectors.toSet());

        assertThat(excludedInPreview).isNotEmpty().isEqualTo(unresolvedInConfirmation);
    }

    @Test
    void queryCountDoesNotGrowWithAudienceSize() {
        PersonEnrollmentTestFixtures fixtures = new PersonEnrollmentTestFixtures(entityManager);
        for (int i = 0; i < 3; i++) {
            enroll(fixtures, preceptor(), EnrollmentStatus.ENROLLED);
        }
        entityManager.clear();
        long fewQueries = countQueriesOfPreview();

        for (int i = 0; i < 40; i++) {
            Person person = preceptor();
            if (i % 2 == 0) {
                enroll(fixtures, person, EnrollmentStatus.ENROLLED);
            }
        }
        entityManager.clear();
        long manyQueries = countQueriesOfPreview();

        assertThat(manyQueries).isEqualTo(fewQueries);
    }

    private long countQueriesOfPreview() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            preview(true);
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}

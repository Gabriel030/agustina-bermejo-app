package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.operation.CohortPromotionAcquisitionPort;
import com.red_escuela.backend.billing.operation.CohortPromotionAcquisitionRequest;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractCohortPromotionAcquisitionIT extends AbstractIntegrationTest {
    protected static final LocalDate ENROLLMENT_DATE = LocalDate.of(2027, 2, 1);

    @Autowired
    protected CohortPromotionAcquisitionPort acquisitionPort;
    @Autowired
    protected DiscountDefinitionRepository definitionRepository;
    @Autowired
    protected IssuingScenarioFactory factory;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected PersonRepository personRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected AddressRepository addressRepository;
    @Autowired
    protected NotificationRepository notificationRepository;

    protected IssuingScenarioFactory.Offering offering;
    protected Person director;
    private final List<Long> definitionIds = new ArrayList<>();
    private final List<Long> studentIds = new ArrayList<>();

    @BeforeEach
    void setUpAcquisitionScenario() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 2, 1));
        offering = factory.newProgram(Currency.ARS, 80000, 50000);
        director = persistDirector();
    }

    @AfterEach
    void tearDownAcquisitionScenario() {
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        jdbc.execute("alter table discount_definition_status_change disable trigger trg_discount_definition_status_change_append_only");
        try {
            studentIds.forEach(studentId -> {
                jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where student_id = ?)", studentId);
                jdbc.update("delete from student_discount where student_id = ?", studentId);
            });
            definitionIds.forEach(definitionId -> {
                jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where discount_definition_id = ?)", definitionId);
                jdbc.update("delete from student_discount where discount_definition_id = ?", definitionId);
                jdbc.update("delete from discount_definition_status_change where discount_definition_id = ?", definitionId);
                jdbc.update("delete from discount_definition where id = ?", definitionId);
            });
        } finally {
            jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
            jdbc.execute("alter table discount_definition_status_change enable trigger trg_discount_definition_status_change_append_only");
        }
        jdbc.update("delete from notification where recipient_id = ?", director.getId());
    }

    protected Person persistDirector() {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = "acquisition.director." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Director");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("DIRECTOR").orElseThrow());
            return personRepository.save(person);
        });
    }

    protected DiscountDraftBuilder promotion(String name, String percent, Integer quota) {
        return DiscountDraftBuilder.cohortPromotion().named(name).forProgram(offering.programId()).withPercent(percent).withInstallments(3)
                .availableFrom(LocalDate.of(2027, 1, 1)).withEnrollmentWindow(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 2, 28)).withQuota(quota);
    }

    protected Long publish(DiscountDraftBuilder builder) {
        Long id = transactionTemplate.execute(status -> definitionRepository.save(
                DiscountDefinition.publishDiscountDefinition(builder.build(), director.getId(), Instant.now())).getId());
        definitionIds.add(id);
        return id;
    }

    protected IssuingScenarioFactory.Learner newLearner() {
        IssuingScenarioFactory.Learner learner = factory.newLearner(offering, null);
        studentIds.add(learner.studentId());
        return learner;
    }

    protected void acquire(IssuingScenarioFactory.Learner learner) {
        acquisitionPort.acquireCohortPromotionWhenEligible(CohortPromotionAcquisitionRequest.forProgramEnrollment(
                learner.programEnrollmentId(), offering.programId(), null, ENROLLMENT_DATE, director.getId()));
    }

    protected List<Map<String, Object>> acquisitionsOf(IssuingScenarioFactory.Learner learner) {
        return jdbc.queryForList("select id, discount_definition_id, status, discount_type, origin from student_discount where program_enrollment_id = ? and origin = 'COHORT_ACQUISITION'",
                learner.programEnrollmentId());
    }

    protected long placesUsed(Long definitionId) {
        return jdbc.queryForObject("select count(*) from student_discount where discount_definition_id = ? and origin = 'COHORT_ACQUISITION'", Long.class, definitionId);
    }

    protected boolean quotaExhaustionWasNotified(Long definitionId) {
        return jdbc.queryForObject("select quota_exhausted_notified_at is not null from discount_definition where id = ?", Boolean.class, definitionId);
    }

    protected List<Notification> quotaExhaustedNoticesFor(Long definitionId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(director.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.DISCOUNT_QUOTA_EXHAUSTED && definitionId.equals(notification.getRelatedEntityId()))
                .toList();
    }
}

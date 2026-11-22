package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.ApproveSectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.RejectSectionEvaluationConfigCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.RejectSectionEvaluationConfigUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.SaveSectionEvaluationConfigDraftCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.SaveSectionEvaluationConfigDraftUseCase;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.SubmitSectionEvaluationConfigForApprovalCommand;
import com.red_escuela.backend.evaluation.usecase.sectionconfig.SubmitSectionEvaluationConfigForApprovalUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SectionEvaluationConfigTransactionBoundaryIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FailingListenerConfiguration {
        @Bean
        FailingConfigListener failingConfigListener() {
            return new FailingConfigListener();
        }
    }

    static class FailingConfigListener {
        private volatile Class<?> failingOn;

        void failOn(Class<?> eventType) {
            this.failingOn = eventType;
        }

        void stopFailing() {
            this.failingOn = null;
        }

        @EventListener
        void onProposed(SectionEvaluationConfigProposedEvent event) {
            failIfArmedFor(SectionEvaluationConfigProposedEvent.class);
        }

        @EventListener
        void onApproved(SectionEvaluationConfigApprovedEvent event) {
            failIfArmedFor(SectionEvaluationConfigApprovedEvent.class);
        }

        @EventListener
        void onRejected(SectionEvaluationConfigRejectedEvent event) {
            failIfArmedFor(SectionEvaluationConfigRejectedEvent.class);
        }

        private void failIfArmedFor(Class<?> eventType) {
            if (eventType.equals(failingOn)) {
                throw new IllegalStateException("listener failed on " + eventType.getSimpleName());
            }
        }
    }

    @Autowired
    private SaveSectionEvaluationConfigDraftUseCase saveDraft;

    @Autowired
    private SubmitSectionEvaluationConfigForApprovalUseCase submitForApproval;

    @Autowired
    private ApproveSectionEvaluationConfigUseCase approveConfig;

    @Autowired
    private RejectSectionEvaluationConfigUseCase rejectConfig;

    @Autowired
    private SubjectSectionService subjectSectionService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FailingConfigListener failingListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long sectionId;
    private Long instructorId;

    @BeforeEach
    void seedASectionWithALeadInstructorAndADirector() {
        long suffix = System.nanoTime() % 100000000L;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Subject subject = new Subject();
            subject.setStatus(SubjectStatus.ACTIVE);
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Materia " + suffix);
            entityManager.persist(subject);

            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setDescription("2026-1C");
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.now().plusDays(30));
            term.setEndDate(LocalDateTime.now().plusMonths(4));
            entityManager.persist(term);
            entityManager.flush();

            SubjectSection section = subjectSectionService.create(subject.getId(),
                    new CreateSectionRequest("Comision " + suffix, term.getId(), null, 30, null));
            sectionId = section.getId();

            Instructor instructor = new Instructor();
            instructor.setPerson(persistPerson("teacher" + suffix, "DOCENTE"));
            entityManager.persist(instructor);
            instructorId = instructor.getId();
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);

            Person director = persistPerson("director" + suffix, "DIRECTOR");
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(director.getId().toString(), null));
        });
        saveDraft.save(new SaveSectionEvaluationConfigDraftCommand(sectionId, instructorId,
                new UpdateSectionEvaluationConfigRequest(2, LocalDate.now().plusDays(60), null, null,
                        LocalDate.now().plusDays(45), LocalDate.now().plusDays(50), null, null,
                        LocalDate.now().plusDays(80), LocalDate.now().plusDays(92))));
    }

    @AfterEach
    void cleanUp() {
        failingListener.stopFailing();
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String key, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(key);
        person.setDni(key);
        person.setUsername(key);
        person.setEmail(key + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        return person;
    }

    private String configStatus() {
        return jdbcTemplate.queryForObject(
                "select status from section_evaluation_config where subject_section_id = ?", String.class, sectionId);
    }

    private long examBoardCount() {
        return jdbcTemplate.queryForObject("select count(*) from exam_board", Long.class);
    }

    private void submitConfig() {
        submitForApproval.submit(new SubmitSectionEvaluationConfigForApprovalCommand(sectionId, instructorId));
    }

    @Test
    void submittingFailingWhilePublishingTheEventKeepsTheConfigInDraft() {
        failingListener.failOn(SectionEvaluationConfigProposedEvent.class);

        assertThatThrownBy(this::submitConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(configStatus()).isEqualTo("DRAFT");
    }

    @Test
    void approvingFailingWhilePublishingTheEventLeavesTheConfigPendingAndCreatesNoFinalCalls() {
        submitConfig();
        failingListener.failOn(SectionEvaluationConfigApprovedEvent.class);
        long boardsBefore = examBoardCount();

        assertThatThrownBy(() -> approveConfig.approve(new ApproveSectionEvaluationConfigCommand(sectionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(configStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(examBoardCount()).isEqualTo(boardsBefore);
    }

    @Test
    void rejectingFailingWhilePublishingTheEventLeavesTheConfigPending() {
        submitConfig();
        failingListener.failOn(SectionEvaluationConfigRejectedEvent.class);

        assertThatThrownBy(() -> rejectConfig.reject(new RejectSectionEvaluationConfigCommand(sectionId,
                new RejectSectionEvaluationConfigRequest("Fechas incorrectas"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(configStatus()).isEqualTo("PENDING_APPROVAL");
    }
}

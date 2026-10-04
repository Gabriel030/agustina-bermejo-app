package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.EvaluationDateOnHolidayException;
import com.red_escuela.backend.evaluation.exception.EvaluationDateOutsideTermException;
import com.red_escuela.backend.evaluation.exception.InvalidEvaluationDateException;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.exception.NotSectionLeadInstructorException;
import com.red_escuela.backend.evaluation.exception.SubmissionWindowClosedException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SectionEvaluationConfigUseCaseTest extends AbstractIntegrationTest {

    @Autowired
    private GetMySectionEvaluationConfigUseCase getMySectionEvaluationConfigUseCase;

    @Autowired
    private SaveSectionEvaluationConfigDraftUseCase saveSectionEvaluationConfigDraftUseCase;

    @Autowired
    private SubmitSectionEvaluationConfigForApprovalUseCase submitSectionEvaluationConfigForApprovalUseCase;

    @Autowired
    private ApproveSectionEvaluationConfigUseCase approveSectionEvaluationConfigUseCase;

    @Autowired
    private RejectSectionEvaluationConfigUseCase rejectSectionEvaluationConfigUseCase;

    @Autowired
    private SubjectSectionService subjectSectionService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ExamBoardRepository examBoardRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private HolidayRepository holidayRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPersonBase(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Person persistPersonWithRole(String dni, String roleName) {
        Person person = persistPersonBase(dni);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        return person;
    }

    private Instructor persistInstructor(String dni) {
        Instructor instructor = new Instructor();
        instructor.setPerson(persistPersonWithRole(dni, "DOCENTE"));
        entityManager.persist(instructor);
        return instructor;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm(LocalDateTime start, LocalDateTime end) {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(start);
        term.setEndDate(end);
        entityManager.persist(term);
        return term;
    }

    private void assignInstructor(SubjectSection section, Instructor instructor, InstructorRole role) {
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(role);
        entityManager.persist(assignment);
    }

    private LocalDate nonHolidayDate(int daysFromNow) {
        LocalDate date = LocalDate.now().plusDays(daysFromNow);
        while (holidayRepository.existsByDate(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    private UpdateSectionEvaluationConfigRequest validRequest(LocalDate exam1, LocalDate exam2, LocalDate finalCall) {
        LocalDate tp1DueDate = exam1.plusDays(20);
        while (holidayRepository.existsByDate(tp1DueDate)) {
            tp1DueDate = tp1DueDate.plusDays(1);
        }
        return new UpdateSectionEvaluationConfigRequest(2, tp1DueDate, null, null, exam1, exam2, null, null, finalCall, null);
    }

    @Test
    void creatingSectionAutomaticallyCreatesConfigInDraft() {
        Subject subject = persistSubject("MateriaConfigAuto");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();

        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        entityManager.flush();

        SectionEvaluationConfig config = getMySectionEvaluationConfigUseCase.get(new GetMySectionEvaluationConfigCommand(section.getId(),
                persistAssignedInstructor(section)));
        assertThat(config.getStatus()).isEqualTo(SectionEvaluationConfigStatus.DRAFT);
    }

    private Long persistAssignedInstructor(SubjectSection section) {
        Instructor instructor = persistInstructor("90000000");
        assignInstructor(section, instructor, InstructorRole.LEAD);
        entityManager.flush();
        return instructor.getId();
    }

    @Test
    void instructorNotInSectionCannotSaveDraft() {
        Subject subject = persistSubject("MateriaAjena");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor outsider = persistInstructor("90000001");
        entityManager.flush();

        assertThatThrownBy(() -> saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), outsider.getId(),
                validRequest(nonHolidayDate(40), nonHolidayDate(60), nonHolidayDate(80)))))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void assistantInstructorCannotSaveDraftOnlyLeadCan() {
        Subject subject = persistSubject("MateriaAdjunto");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor assistant = persistInstructor("90000002");
        assignInstructor(section, assistant, InstructorRole.ASSISTANT);
        entityManager.flush();

        assertThatThrownBy(() -> saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), assistant.getId(),
                validRequest(nonHolidayDate(40), nonHolidayDate(60), nonHolidayDate(80)))))
                .isInstanceOf(NotSectionLeadInstructorException.class);
    }

    @Test
    void savingADateOutsideTermRangeFails() {
        LocalDateTime start = LocalDateTime.now().plusDays(30);
        LocalDateTime end = LocalDateTime.now().plusMonths(4);
        Subject subject = persistSubject("MateriaFueraDeRango");
        Term term = persistTerm(start, end);
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000003");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        entityManager.flush();

        LocalDate dateOutsideRange = end.toLocalDate().plusDays(5);
        assertThatThrownBy(() -> saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                validRequest(dateOutsideRange, dateOutsideRange.plusDays(10), dateOutsideRange.plusDays(20)))))
                .isInstanceOf(InvalidEvaluationDateException.class);
    }

    @Test
    void annualSectionCanSaveFourExamDates() {
        Subject subject = persistSubject("MateriaAnual");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(9));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000013");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        entityManager.flush();

        SectionEvaluationConfig saved = saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                new UpdateSectionEvaluationConfigRequest(2, nonHolidayDate(40), null, null,
                        nonHolidayDate(60), nonHolidayDate(120),
                        nonHolidayDate(180), nonHolidayDate(240),
                        nonHolidayDate(250), null)));

        assertThat(saved.getExam3Date()).isEqualTo(nonHolidayDate(180));
        assertThat(saved.getExam4Date()).isEqualTo(nonHolidayDate(240));
    }

    @Test
    void savingADateThatFallsOnAHolidayFails() {
        LocalDate holidayDate = nonHolidayDate(45);
        Holiday holiday = new Holiday();
        holiday.setDate(holidayDate);
        holiday.setDescription("Feriado de prueba");
        entityManager.persist(holiday);

        Subject subject = persistSubject("MateriaFeriado");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000004");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        entityManager.flush();

        assertThatThrownBy(() -> saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                validRequest(holidayDate, holidayDate.plusDays(20), holidayDate.plusDays(40)))))
                .isInstanceOf(InvalidEvaluationDateException.class);
    }

    @Test
    void submittingAfterTermStartedFails() {
        Subject subject = persistSubject("MateriaVentanaVencida");
        Term term = persistTerm(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000005");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        entityManager.flush();

        assertThatThrownBy(() -> submitSectionEvaluationConfigForApprovalUseCase.submit(new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId())))
                .isInstanceOf(SubmissionWindowClosedException.class);
    }

    @Test
    void submittingNotifiesDirectorRole() {
        Subject subject = persistSubject("MateriaNotificaDirector");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000006");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        Person director = persistPersonWithRole("90000007", "DIRECTOR");
        entityManager.flush();

        saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                validRequest(nonHolidayDate(40), nonHolidayDate(60), nonHolidayDate(80))));
        SectionEvaluationConfig submitted = submitSectionEvaluationConfigForApprovalUseCase.submit(new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId()));

        assertThat(submitted.getStatus()).isEqualTo(SectionEvaluationConfigStatus.PENDING_APPROVAL);
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(director.getId());
        assertThat(notifications).extracting(Notification::getType)
                .contains(NotificationType.SECTION_EVAL_CONFIG_PROPOSED);
    }

    @Test
    void approvingCreatesBothFinalCallsAndNotifiesInstructor() {
        Subject subject = persistSubject("MateriaAprobada");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000008");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        Person actor = persistPersonWithRole("90000009", "DIRECTOR");
        entityManager.flush();

        LocalDate finalCall1 = nonHolidayDate(80);
        LocalDate finalCall2 = nonHolidayDate(92);
        saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                new UpdateSectionEvaluationConfigRequest(2, nonHolidayDate(40), null, null,
                        nonHolidayDate(45), nonHolidayDate(60), null, null, finalCall1, finalCall2)));
        submitSectionEvaluationConfigForApprovalUseCase.submit(new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId()));

        SectionEvaluationConfig approved = approveSectionEvaluationConfigUseCase.approve(new ApproveSectionEvaluationConfigCommand(section.getId(), actor));

        assertThat(approved.getStatus()).isEqualTo(SectionEvaluationConfigStatus.APPROVED);
        List<ExamBoard> examBoards = examBoardRepository.findBySubjectIdAndStatus(subject.getId(), com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED);
        assertThat(examBoards).hasSize(2);
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(leadInstructor.getPerson().getId());
        assertThat(notifications).extracting(Notification::getType)
                .contains(NotificationType.SECTION_EVAL_CONFIG_APPROVED);
    }

    @Test
    void approvingCreatesFinalCallsAtTheSectionTimeAndClassroomWithARegistrationWindow() {
        Subject subject = persistSubject("MateriaMesasCompletas");
        Term term = persistTerm(LocalDateTime.of(2099, 3, 2, 0, 0), LocalDateTime.of(2099, 12, 11, 0, 0));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000024");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        Person actor = persistPersonWithRole("90000025", "DIRECTOR");
        LocalDate finalCall1 = LocalDate.of(2099, 12, 2);
        LocalDate finalCall2 = LocalDate.of(2099, 12, 7);
        Classroom classroom = new Classroom();
        classroom.setName("Aula QA 5");
        classroom.setCapacity(30);
        entityManager.persist(classroom);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(finalCall1.getDayOfWeek());
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        entityManager.persist(slot);
        entityManager.flush();

        saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                new UpdateSectionEvaluationConfigRequest(1, LocalDate.of(2099, 4, 24), null, null,
                        LocalDate.of(2099, 5, 15), LocalDate.of(2099, 9, 11), null, null, finalCall1, finalCall2)));
        submitSectionEvaluationConfigForApprovalUseCase.submit(new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId()));
        approveSectionEvaluationConfigUseCase.approve(new ApproveSectionEvaluationConfigCommand(section.getId(), actor));

        List<ExamBoard> examBoards = examBoardRepository.findBySubjectIdAndStatus(subject.getId(), com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED);
        assertThat(examBoards).extracting(ExamBoard::getExamDate)
                .containsExactlyInAnyOrder(finalCall1.atTime(18, 0), finalCall2.atTime(18, 0));
        assertThat(examBoards).extracting(ExamBoard::getClassroom).containsOnly("Aula QA 5");
        assertThat(examBoards).allSatisfy(board -> {
            assertThat(board.getRegistrationStart()).isNotNull();
            assertThat(board.getRegistrationEnd())
                    .isEqualTo(board.getExamDate().toLocalDate().minusDays(2).atTime(23, 59));
        });
    }

    @Test
    void rejectingReturnsToDraftAndAllowsResubmission() {
        Subject subject = persistSubject("MateriaRechazada");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Instructor leadInstructor = persistInstructor("90000010");
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        Person actor = persistPersonWithRole("90000011", "DIRECTOR");
        entityManager.flush();

        saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                validRequest(nonHolidayDate(40), nonHolidayDate(60), nonHolidayDate(80))));
        submitSectionEvaluationConfigForApprovalUseCase.submit(new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId()));

        SectionEvaluationConfig rejected = rejectSectionEvaluationConfigUseCase.reject(new RejectSectionEvaluationConfigCommand(section.getId(), actor,
                new RejectSectionEvaluationConfigRequest("Faltan fechas de TP")));
        assertThat(rejected.getStatus()).isEqualTo(SectionEvaluationConfigStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Faltan fechas de TP");

        SectionEvaluationConfig updated = saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(section.getId(), leadInstructor.getId(),
                validRequest(nonHolidayDate(41), nonHolidayDate(61), nonHolidayDate(81))));
        assertThat(updated.getStatus()).isEqualTo(SectionEvaluationConfigStatus.REJECTED);
        assertThat(updated.getRejectionReason()).isEqualTo("Faltan fechas de TP");

        SectionEvaluationConfig resubmitted = submitSectionEvaluationConfigForApprovalUseCase.submit(
                new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId()));
        assertThat(resubmitted.getStatus()).isEqualTo(SectionEvaluationConfigStatus.PENDING_APPROVAL);
        assertThat(resubmitted.getRejectionReason()).isNull();
    }

    private SubjectSection sectionInPastTermWithLead(String subjectName, Instructor leadInstructor) {
        Subject subject = persistSubject(subjectName);
        Term term = persistTerm(LocalDateTime.of(2020, 3, 2, 0, 0), LocalDateTime.of(2020, 12, 11, 0, 0));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        assignInstructor(section, leadInstructor, InstructorRole.LEAD);
        entityManager.flush();
        return section;
    }

    private UpdateSectionEvaluationConfigRequest requestWithinPastTerm(LocalDate finalCall2) {
        return new UpdateSectionEvaluationConfigRequest(1, LocalDate.of(2020, 4, 24), null, null,
                LocalDate.of(2020, 5, 15), LocalDate.of(2020, 9, 11), null, null,
                LocalDate.of(2020, 12, 2), finalCall2);
    }

    @Test
    void rejectedConfigCanBeResubmittedAfterTermStarted() {
        Instructor leadInstructor = persistInstructor("90000020");
        SubjectSection section = sectionInPastTermWithLead("MateriaReenvioEnCurso", leadInstructor);
        SectionEvaluationConfig config = getMySectionEvaluationConfigUseCase.get(
                new GetMySectionEvaluationConfigCommand(section.getId(), leadInstructor.getId()));
        config.setStatus(SectionEvaluationConfigStatus.REJECTED);
        config.setRejectionReason("Corregí el 2do llamado");
        entityManager.flush();

        SectionEvaluationConfig saved = saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(
                section.getId(), leadInstructor.getId(), requestWithinPastTerm(LocalDate.of(2020, 12, 10))));
        assertThat(saved.getStatus()).isEqualTo(SectionEvaluationConfigStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo("Corregí el 2do llamado");

        SectionEvaluationConfig resubmitted = submitSectionEvaluationConfigForApprovalUseCase.submit(
                new SubmitSectionEvaluationConfigForApprovalCommand(section.getId(), leadInstructor.getId()));

        assertThat(resubmitted.getStatus()).isEqualTo(SectionEvaluationConfigStatus.PENDING_APPROVAL);
        assertThat(resubmitted.getRejectionReason()).isNull();
        assertThat(resubmitted.getSubmittedAt()).isNotNull();
    }

    @Test
    void draftOfAStartedTermCannotBeSubmittedButRejectedOneCan() {
        Instructor leadInstructor = persistInstructor("90000021");
        SubjectSection section = sectionInPastTermWithLead("MateriaVentanaCerrada", leadInstructor);
        SectionEvaluationConfig config = getMySectionEvaluationConfigUseCase.get(
                new GetMySectionEvaluationConfigCommand(section.getId(), leadInstructor.getId()));
        LocalDateTime now = LocalDateTime.of(2020, 6, 1, 10, 0);

        assertThat(config.canBeSubmitted(now)).isFalse();
        config.setStatus(SectionEvaluationConfigStatus.REJECTED);
        assertThat(config.canBeSubmitted(now)).isTrue();
        config.setStatus(SectionEvaluationConfigStatus.PENDING_APPROVAL);
        assertThat(config.canBeSubmitted(now)).isFalse();
    }

    @Test
    void savingADateAfterTermEndReportsItIsOutsideTheTerm() {
        Instructor leadInstructor = persistInstructor("90000022");
        SubjectSection section = sectionInPastTermWithLead("MateriaFinalFueraDelCiclo", leadInstructor);

        assertThatThrownBy(() -> saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(
                section.getId(), leadInstructor.getId(), requestWithinPastTerm(LocalDate.of(2020, 12, 16)))))
                .isInstanceOf(EvaluationDateOutsideTermException.class);
    }

    @Test
    void savingAHolidayDateReportsItFallsOnAHoliday() {
        Holiday holiday = new Holiday();
        holiday.setDate(LocalDate.of(2020, 12, 9));
        holiday.setDescription("Feriado de prueba 2020");
        entityManager.persist(holiday);
        Instructor leadInstructor = persistInstructor("90000023");
        SubjectSection section = sectionInPastTermWithLead("MateriaFinalEnFeriado", leadInstructor);

        assertThatThrownBy(() -> saveSectionEvaluationConfigDraftUseCase.save(new SaveSectionEvaluationConfigDraftCommand(
                section.getId(), leadInstructor.getId(), requestWithinPastTerm(LocalDate.of(2020, 12, 9)))))
                .isInstanceOf(EvaluationDateOnHolidayException.class);
    }

    @Test
    void approvingAConfigNotPendingApprovalFails() {
        Subject subject = persistSubject("MateriaEstadoInvalido");
        Term term = persistTerm(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusMonths(4));
        entityManager.flush();
        SubjectSection section = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, null));
        Person actor = persistPersonWithRole("90000012", "DIRECTOR");
        entityManager.flush();

        assertThatThrownBy(() -> approveSectionEvaluationConfigUseCase.approve(new ApproveSectionEvaluationConfigCommand(section.getId(), actor)))
                .isInstanceOf(InvalidSectionEvaluationConfigTransitionException.class);
    }
}

package com.red_escuela.backend.evaluation.specialist;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceTestData;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SlowTest(reason = "ocho escenarios de punta a punta por MockMvc con fixtures completos y una carrera de transacciones concurrentes")
class CommissionClosureQaIT extends AbstractIntegrationTest {

    private static final LocalDateTime TERM_END_2026 = LocalDateTime.of(2026, 12, 15, 23, 59);
    private static final int[][] DEFAULT_SCORES = {{8, 9}, {8, 9}, {5, 5}, {2, 2}, {8, 9}};
    private static final String[] ACCENTED_LASTNAMES = {"Muñoz Peña", "Núñez", "Ávila", "Ibáñez", "Güemes"};
    private static final String APPROVE_BODY = "{\"book\":\"Libro 3\",\"folio\":\"Folio 12\",\"closeWithoutTeacherConfirmation\":false}";
    private static final String REASON_BODY = "{\"reason\":\"Se corrigió una nota del segundo parcial\"}";
    private static final String MONEY_KEYS = "(?is).*(amount|importe|monto|price|deuda|currency).*";

    private record Commission(Long sectionId, Long teacherPersonId, Long teacherInstructorId, List<Long> enrollmentIds,
            List<Long> studentPersonIds, List<Student> students, List<ProgramEnrollment> programEnrollments,
            ProgramSubject targetEntry, Program program, Subject subject, EvaluationScheme scheme) {
    }

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;
    @Autowired
    private ProgramPrerequisiteEvaluator prerequisiteEvaluator;
    @Autowired
    private FinalExamPrerequisiteChecker finalExamPrerequisiteChecker;
    @Autowired
    private StudentCurrentAccountService accountService;
    @Autowired
    private InvoiceNumberGenerator numberGenerator;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private ClosureConfirmationReminderJob reminderJob;
    @Autowired
    private CommissionClosureAuditRepository auditRepository;

    private int sequence = 84000000;
    private int labelSequence = 0;
    private Institution institution;

    @AfterEach
    void restoreBillingFull() {
        committed(() -> jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'"));
        snapshotHolder.refresh();
    }

    private void committed(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }

    private void switchBillingTo(String mode) {
        boolean enabled = !mode.equals("OFF");
        committed(() -> jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode));
        snapshotHolder.refresh();
    }

    private Person persistPerson(String lastname, String name, String roleName) {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name == null ? "Name" + dni : name);
        person.setLastname(lastname == null ? "Last" + dni : lastname);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        if (roleName != null) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            person.getRoles().add(role);
        }
        entityManager.persist(person);
        return person;
    }

    private Long personWithRole(String roleName) {
        Person person = persistPerson(null, null, roleName);
        entityManager.flush();
        return person.getId();
    }

    private Student persistStudent(String lastname, String name) {
        Student student = new Student();
        student.setPerson(persistPerson(lastname, name, "ALUMNO"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        for (DocumentType type : DocumentType.values()) {
            StudentDocument document = new StudentDocument();
            document.setStudent(student);
            document.setType(type);
            document.setFilePath("documents/" + student.getId() + "/" + type);
            document.setUploadedAt(LocalDateTime.now());
            entityManager.persist(document);
        }
        return student;
    }

    private Commission commission(LocalDateTime termEnd, String[] lastnames, int[][] scores, int withdrawnIndex) {
        int label = labelSequence++;
        institution = new Institution();
        institution.setName("Qa institute " + label);
        entityManager.persist(institution);
        Program program = new Program();
        program.setName("Programa de Educación Inicial " + label);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Teorías Socioculturales " + label);
        entityManager.persist(subject);
        ProgramSubject entry = new ProgramSubject();
        entry.setProgram(program);
        entry.setSubject(subject);
        entry.setCredits(4);
        entry.setStatus(StatusType.ACTIVE);
        entityManager.persist(entry);
        Term term = TermFixtures.newTerm();
        term.setYear(termEnd.getYear());
        term.setDescription(termEnd.getYear() + "-QA" + label);
        term.setEndDate(termEnd);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Scheme " + TestSubjectCodes.next());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6)));
        scheme.setPromotionMinGrade(Grade.of(BigDecimal.valueOf(7)));
        entityManager.persist(scheme);
        SubjectSection section = new SubjectSection();
        section.setName("Comisión Única " + label);
        section.setSubject(subject);
        section.setTerm(term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setStatus(StatusType.ACTIVE);
        section.setEvaluationScheme(scheme);
        entityManager.persist(section);
        Person teacherPerson = persistPerson("Docente", "Zoé", "DOCENTE");
        Instructor teacher = new Instructor();
        teacher.setPerson(teacherPerson);
        entityManager.persist(teacher);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(teacher);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        List<Long> enrollmentIds = new ArrayList<>();
        List<Long> studentPersonIds = new ArrayList<>();
        List<Student> students = new ArrayList<>();
        List<ProgramEnrollment> programEnrollments = new ArrayList<>();
        for (int index = 0; index < scores.length; index++) {
            Student student = persistStudent(lastnames[index], "Íñigo " + index);
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(section);
            enrollment.setStatus(index == withdrawnIndex ? EnrollmentStatus.WITHDRAWN : EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            persistExam(enrollment, 1, scores[index][0]);
            persistExam(enrollment, 2, scores[index][1]);
            enrollmentIds.add(enrollment.getId());
            studentPersonIds.add(student.getPerson().getId());
            students.add(student);
            programEnrollments.add(programEnrollment);
        }
        entityManager.persist(SectionGradeItemPublication.of(
                SectionKind.PROGRAM, section.getId(), GradeItemType.EXAM, 1, LocalDateTime.now(), null));
        entityManager.persist(SectionGradeItemPublication.of(
                SectionKind.PROGRAM, section.getId(), GradeItemType.EXAM, 2, LocalDateTime.now(), null));
        entityManager.flush();
        return new Commission(section.getId(), teacherPerson.getId(), teacher.getId(), enrollmentIds, studentPersonIds,
                students, programEnrollments, entry, program, subject, scheme);
    }

    private Commission commission(LocalDateTime termEnd) {
        return commission(termEnd, ACCENTED_LASTNAMES, DEFAULT_SCORES, -1);
    }

    private void persistExam(SubjectSectionEnrollment enrollment, int sequenceNumber, int score) {
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(sequenceNumber);
        item.setWeight(0.5);
        item.setDate(LocalDateTime.now());
        item.setScore(Grade.of(BigDecimal.valueOf(score)));
        entityManager.persist(item);
    }

    private MvcResult call(Long personId, MockHttpServletRequestBuilder request) throws Exception {
        String token = jwtService.generateAccessToken(entityManager.find(Person.class, personId));
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
    }

    private int statusOf(Long personId, MockHttpServletRequestBuilder request) throws Exception {
        return call(personId, request).getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String sheetOf(Long personId, Commission commission) throws Exception {
        MvcResult result = call(personId, get("/program-sections/" + commission.sectionId() + "/closure-sheet"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getContentAsString();
    }

    private String rowField(String sheet, Long enrollmentId, String field) {
        List<Object> values = JsonPath.read(sheet, "$.students[?(@.enrollmentId==" + enrollmentId + ")]." + field);
        return values.isEmpty() ? null : String.valueOf(values.get(0));
    }

    private void confirmAsTeacher(Commission commission) throws Exception {
        sheetOf(commission.teacherPersonId(), commission);
        assertThat(statusOf(commission.teacherPersonId(),
                post("/program-sections/" + commission.sectionId() + "/closure/confirm"))).isEqualTo(204);
    }

    private void approveAs(Long personId, Commission commission, String body) throws Exception {
        assertThat(statusOf(personId, json(post("/program-sections/" + commission.sectionId() + "/closure/approve"), body)))
                .isEqualTo(204);
    }

    private Long closureIdOf(String sheet) {
        return ((Number) JsonPath.read(sheet, "$.closureId")).longValue();
    }

    @Test
    void fullFlowOverHttpAssignsOneActaPerYearAndKeepsTheNumberWhenReopenedAndClosedAgain() throws Exception {
        Commission first = commission(TERM_END_2026);
        Commission second = commission(TERM_END_2026);
        Commission nextYear = commission(LocalDateTime.of(2027, 6, 30, 23, 59));
        Long admin = personWithRole("ADMINISTRATIVO");
        Long director = personWithRole("DIRECTOR");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));

        confirmAsTeacher(first);
        assertThat(statusOf(director, json(put("/program-sections/" + first.sectionId() + "/closure/students/"
                + first.enrollmentIds().get(2) + "/exception"), "{\"reason\":\"corto\"}"))).isEqualTo(400);
        assertThat(statusOf(director, json(put("/program-sections/" + first.sectionId() + "/closure/students/"
                + first.enrollmentIds().get(0) + "/exception"), "{\"reason\":\"Pago en trámite acordado\"}"))).isEqualTo(204);
        approveAs(admin, first, APPROVE_BODY);
        confirmAsTeacher(second);
        approveAs(admin, second, APPROVE_BODY);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 7, 1));
        confirmAsTeacher(nextYear);
        approveAs(admin, nextYear, APPROVE_BODY);

        String firstSheet = sheetOf(director, first);
        assertThat((String) JsonPath.read(firstSheet, "$.reportNumber")).isEqualTo("ACTA-2026-0001");
        assertThat((String) JsonPath.read(firstSheet, "$.status")).isEqualTo("CLOSED");
        assertThat((String) JsonPath.read(firstSheet, "$.book")).isEqualTo("Libro 3");
        assertThat((String) JsonPath.read(firstSheet, "$.folio")).isEqualTo("Folio 12");
        assertThat((Integer) JsonPath.read(firstSheet, "$.reportVersion")).isEqualTo(1);
        assertThat((String) JsonPath.read(sheetOf(director, second), "$.reportNumber")).isEqualTo("ACTA-2026-0002");
        assertThat((String) JsonPath.read(sheetOf(director, nextYear), "$.reportNumber")).isEqualTo("ACTA-2027-0001");
        entityManager.flush();
        entityManager.clear();
        SubjectSectionEnrollment exempted = entityManager.find(SubjectSectionEnrollment.class, first.enrollmentIds().get(0));
        assertThat(exempted.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(exempted.getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);

        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 21));
        assertThat(statusOf(director, json(post("/program-sections/" + first.sectionId() + "/closure/reopen"), "{\"reason\":\"corto\"}")))
                .isEqualTo(400);
        assertThat(statusOf(director, json(post("/program-sections/" + first.sectionId() + "/closure/reopen"), REASON_BODY)))
                .isEqualTo(204);
        String reopened = sheetOf(director, first);
        assertThat((String) JsonPath.read(reopened, "$.status")).isEqualTo("REOPENED");
        assertThat((Integer) JsonPath.read(reopened, "$.reportVersion")).isEqualTo(2);
        assertThat((String) JsonPath.read(reopened, "$.reportNumber")).isEqualTo("ACTA-2026-0001");
        confirmAsTeacher(first);
        approveAs(admin, first, "{\"book\":\"Libro 3\",\"folio\":\"Folio 13\",\"closeWithoutTeacherConfirmation\":false}");

        String reclosed = sheetOf(director, first);
        assertThat((String) JsonPath.read(reclosed, "$.status")).isEqualTo("CLOSED");
        assertThat((String) JsonPath.read(reclosed, "$.reportNumber")).isEqualTo("ACTA-2026-0001");
        assertThat((Integer) JsonPath.read(reclosed, "$.reportVersion")).isEqualTo(2);
        assertThat((String) JsonPath.read(reclosed, "$.folio")).isEqualTo("Folio 13");
        assertThat(auditRepository.findAll().stream().map(CommissionClosureAudit::getAction).collect(Collectors.toSet()))
                .contains(ClosureAuditAction.EXCEPTION_GRANTED, ClosureAuditAction.CLOSED, ClosureAuditAction.REOPENED);
        assertThat(jdbc.queryForObject("select last_number from closure_report_number_counter where report_year = 2026", Integer.class))
                .isEqualTo(2);
    }

    private void seedOverdueInvoiceFor(Commission commission, int studentIndex, LocalDate dueDate) {
        Long studentId = commission.students().get(studentIndex).getId();
        Long accountId = accountService.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.ARS).getId();
        InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE,
                InvoiceType.ADMINISTRATIVE_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null);
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Concepto", YearMonth.of(2026, 11), null, null,
                null, null, null, "QA:" + System.nanoTime(), NonNegativeMoney.of(new BigDecimal("1000.00"), Currency.ARS));
        Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, InvoiceTestData.ACTOR);
        draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.minusDays(9), dueDate,
                InvoiceTestData.SNAPSHOT, InvoiceTestData.ACTOR);
        Invoice saved = invoiceRepository.saveAndFlush(draft);
        jdbc.update("update invoice_line set program_enrollment_id = ? where invoice_id = ?",
                commission.programEnrollments().get(studentIndex).getId(), saved.getId());
        entityManager.clear();
    }

    @Test
    void paymentsAreNotVerifiedAndNeverBlockWhileBillingIsOffOrMirrorAndBlockPromotionOnlyInFullMode() throws Exception {
        Commission commission = commission(TERM_END_2026);
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        seedOverdueInvoiceFor(commission, 0, LocalDate.of(2026, 12, 1));
        Long director = personWithRole("DIRECTOR");
        Long debtor = commission.enrollmentIds().get(0);
        Long upToDate = commission.enrollmentIds().get(1);

        for (String mode : List.of("OFF", "MIRROR")) {
            switchBillingTo(mode);
            String sheet = sheetOf(director, commission);
            assertThat(rowField(sheet, debtor, "administrative.payments")).as(mode).isEqualTo("NOT_VERIFIED");
            assertThat(rowField(sheet, debtor, "suggestedResult")).as(mode).isEqualTo("PROMOTES");
            assertThat(rowField(sheet, upToDate, "administrative.payments")).as(mode).isEqualTo("NOT_VERIFIED");
            assertThat(rowField(sheet, upToDate, "suggestedResult")).as(mode).isEqualTo("PROMOTES");
        }
        switchBillingTo("FULL");
        String full = sheetOf(director, commission);
        assertThat(rowField(full, debtor, "administrative.payments")).isEqualTo("NOT_OK");
        assertThat(rowField(full, debtor, "suggestedResult")).isEqualTo("PROMOTION_CONDITIONAL");
        assertThat(rowField(full, upToDate, "administrative.payments")).isEqualTo("OK");
        assertThat(rowField(full, upToDate, "suggestedResult")).isEqualTo("PROMOTES");

        String teacherView = sheetOf(commission.teacherPersonId(), commission);
        assertThat(rowField(teacherView, debtor, "administrative.payments")).isEqualTo("NOT_OK");
        assertThat(teacherView).doesNotContain("missingDocuments").doesNotMatch(MONEY_KEYS);
    }

    private Subject addPrerequisite(Commission commission) {
        Subject prerequisite = new Subject();
        prerequisite.setCode(TestSubjectCodes.next());
        prerequisite.setName("Correlativa previa " + prerequisite.getCode());
        entityManager.persist(prerequisite);
        ProgramSubject prerequisiteEntry = new ProgramSubject();
        prerequisiteEntry.setProgram(commission.program());
        prerequisiteEntry.setSubject(prerequisite);
        prerequisiteEntry.setCredits(4);
        prerequisiteEntry.setStatus(StatusType.ACTIVE);
        entityManager.persist(prerequisiteEntry);
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(commission.targetEntry());
        link.setPrerequisiteProgramSubject(prerequisiteEntry);
        link.setRequirementType(RequirementType.PASSED_COURSEWORK);
        entityManager.persist(link);
        return prerequisite;
    }

    private void studentTook(Commission commission, int studentIndex, Subject prerequisite, EnrollmentStatus status) {
        Term past = TermFixtures.newTerm();
        past.setYear(2025);
        past.setDescription("2025-QA" + labelSequence++);
        past.setEndDate(LocalDateTime.of(2025, 12, 15, 23, 59));
        past.setStatus(StatusType.ACTIVE);
        entityManager.persist(past);
        SubjectSection pastSection = new SubjectSection();
        pastSection.setName("Previa " + past.getDescription());
        pastSection.setSubject(prerequisite);
        pastSection.setTerm(past);
        pastSection.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        pastSection.setStatus(StatusType.ACTIVE);
        pastSection.setEvaluationScheme(commission.scheme());
        entityManager.persist(pastSection);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(commission.students().get(studentIndex));
        enrollment.setProgramEnrollment(commission.programEnrollments().get(studentIndex));
        enrollment.setSubjectSection(pastSection);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.of(2025, 3, 1, 9, 0));
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
    }

    @Test
    void aRegularPrerequisiteAllowsTakingTheSubjectButBlocksPromotionAndTheFinalUnlessAWaiverIsApproved() throws Exception {
        Commission commission = commission(TERM_END_2026);
        Subject prerequisite = addPrerequisite(commission);
        studentTook(commission, 0, prerequisite, EnrollmentStatus.REGULAR);
        studentTook(commission, 1, prerequisite, EnrollmentStatus.APPROVED);
        entityManager.flush();
        Long director = personWithRole("DIRECTOR");
        Long subjectId = commission.subject().getId();
        Long regularEnrollment = commission.programEnrollments().get(0).getId();
        Long approvedEnrollment = commission.programEnrollments().get(1).getId();
        Long neverTookEnrollment = commission.programEnrollments().get(2).getId();

        assertThat(prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(regularEnrollment, subjectId)).isTrue();
        assertThat(prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(approvedEnrollment, subjectId)).isTrue();
        assertThat(prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(neverTookEnrollment, subjectId)).isFalse();
        assertThatThrownBy(() -> finalExamPrerequisiteChecker.requireAllApprovedOrWaived(
                regularEnrollment, subjectId, commission.enrollmentIds().get(0))).isNotNull();
        assertThatCode(() -> finalExamPrerequisiteChecker.requireAllApprovedOrWaived(
                approvedEnrollment, subjectId, commission.enrollmentIds().get(1))).doesNotThrowAnyException();
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        String before = sheetOf(director, commission);
        assertThat(rowField(before, commission.enrollmentIds().get(0), "suggestedResult")).isEqualTo("GOES_TO_FINAL");
        assertThat(rowField(before, commission.enrollmentIds().get(0), "alerts")).contains("UNMET_PREREQUISITE");
        assertThat(rowField(before, commission.enrollmentIds().get(1), "suggestedResult")).isEqualTo("PROMOTES");

        Person directorPerson = entityManager.find(Person.class, director);
        DirectorApprovalRequest waiver = DirectorApprovalRequest.open(ExceptionType.PREREQUISITE_WAIVER,
                entityManager.find(ProgramEnrollment.class, regularEnrollment), commission.subject(), "Equivalencia acordada",
                directorPerson, LocalDateTime.now());
        waiver.decide(DirectorApprovalRequestStatus.APPROVED, directorPerson, "Aprobada", LocalDateTime.now());
        entityManager.persist(waiver);
        entityManager.flush();

        assertThatCode(() -> finalExamPrerequisiteChecker.requireAllApprovedOrWaived(
                regularEnrollment, subjectId, commission.enrollmentIds().get(0))).doesNotThrowAnyException();
        String after = sheetOf(director, commission);
        assertThat(rowField(after, commission.enrollmentIds().get(0), "suggestedResult")).isEqualTo("PROMOTES");
    }

    @Test
    void everyRoleSeesOnlyWhatItMayAcrossTheWholeClosureApi() throws Exception {
        Commission commission = commission(TERM_END_2026);
        Long owner = commission.teacherPersonId();
        Long otherTeacher = personWithRole("DOCENTE");
        Instructor stranger = new Instructor();
        stranger.setPerson(entityManager.find(Person.class, otherTeacher));
        entityManager.persist(stranger);
        Long preceptor = personWithRole("PRECEPTOR");
        Long admin = personWithRole("ADMINISTRATIVO");
        Long director = personWithRole("DIRECTOR");
        Long student = commission.studentPersonIds().get(0);
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        String sectionPath = "/program-sections/" + commission.sectionId();
        Long enrollmentId = commission.enrollmentIds().get(0);
        Long closureId = closureIdOf(sheetOf(admin, commission));
        String exportPath = "/commission-closures/" + closureId + "/export";
        String prerequisitesPath = "/commission-closures/" + closureId + "/students/" + enrollmentId + "/prerequisites";

        for (Long allowed : List.of(owner, preceptor, admin, director)) {
            assertThat(statusOf(allowed, get(sectionPath + "/closure-sheet"))).isEqualTo(200);
            assertThat(statusOf(allowed, get(prerequisitesPath))).isEqualTo(200);
            assertThat(statusOf(allowed, get(exportPath).param("format", "xlsx"))).isEqualTo(200);
        }
        assertThat(statusOf(otherTeacher, get(sectionPath + "/closure-sheet"))).isIn(403, 404);
        assertThat(statusOf(otherTeacher, get(prerequisitesPath))).isIn(403, 404);
        assertThat(statusOf(otherTeacher, get(exportPath).param("format", "xlsx"))).isIn(403, 404);
        assertThat(statusOf(otherTeacher, post(sectionPath + "/closure/confirm"))).isIn(403, 404);
        assertThat(statusOf(student, get(sectionPath + "/closure-sheet"))).isEqualTo(403);
        assertThat(statusOf(student, get(prerequisitesPath))).isEqualTo(403);
        assertThat(statusOf(student, get(exportPath).param("format", "xlsx"))).isEqualTo(403);
        for (Long denied : List.of(owner, preceptor, student)) {
            assertThat(statusOf(denied, get("/commission-closures"))).isEqualTo(403);
        }
        for (Long allowed : List.of(admin, director)) {
            assertThat(statusOf(allowed, get("/commission-closures"))).isEqualTo(200);
        }
        for (Long denied : List.of(preceptor, admin, director, student)) {
            assertThat(statusOf(denied, post(sectionPath + "/closure/confirm"))).isEqualTo(403);
            assertThat(statusOf(denied, json(put(sectionPath + "/closure/students/" + enrollmentId + "/observation"),
                    "{\"observation\":\"Observación\"}"))).isEqualTo(403);
        }
        for (Long denied : List.of(owner, otherTeacher, preceptor, director, student)) {
            assertThat(statusOf(denied, json(post(sectionPath + "/closure/approve"), APPROVE_BODY))).isEqualTo(403);
        }
        for (Long denied : List.of(owner, preceptor, admin, student)) {
            assertThat(statusOf(denied, json(post(sectionPath + "/closure/reopen"), REASON_BODY))).isEqualTo(403);
            assertThat(statusOf(denied, json(put(sectionPath + "/closure/students/" + enrollmentId + "/exception"), REASON_BODY)))
                    .isEqualTo(403);
            assertThat(statusOf(denied, delete(sectionPath + "/closure/students/" + enrollmentId + "/exception"))).isEqualTo(403);
        }
        assertThat((String) JsonPath.read(sheetOf(admin, commission), "$.status")).isEqualTo("DRAFT_AVAILABLE");

        String teacherSheet = sheetOf(owner, commission);
        assertThat(teacherSheet).doesNotContain("missingDocuments").doesNotMatch(MONEY_KEYS);
        assertThat(sheetOf(admin, commission)).contains("missingDocuments");
        MvcResult teacherExport = call(owner, get(exportPath).param("format", "xlsx"));
        assertThat(spreadsheetTextOf(teacherExport.getResponse().getContentAsByteArray()))
                .contains("Apellido").doesNotContain("Pagos al día", "Documentos faltantes");
        assertThat(spreadsheetTextOf(call(admin, get(exportPath).param("format", "xlsx")).getResponse().getContentAsByteArray()))
                .contains("Pagos al día");
        assertThat(statusOf(preceptor, get(exportPath).param("format", "pdf"))).isEqualTo(409);
        assertThat(statusOf(owner, get(exportPath).param("format", "docx"))).isEqualTo(400);
    }

    @Test
    void aStudentWhoWithdrewAfterTheTeacherConfirmationIsNotPromotedAndStaysListedAsWithdrawn() throws Exception {
        Commission commission = commission(TERM_END_2026);
        Long admin = personWithRole("ADMINISTRATIVO");
        Long director = personWithRole("DIRECTOR");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        confirmAsTeacher(commission);
        SubjectSectionEnrollment withdrawing = entityManager.find(SubjectSectionEnrollment.class, commission.enrollmentIds().get(0));
        withdrawing.setStatus(EnrollmentStatus.WITHDRAWN);
        entityManager.flush();

        approveAs(admin, commission, APPROVE_BODY);
        entityManager.flush();
        entityManager.clear();

        SubjectSectionEnrollment after = entityManager.find(SubjectSectionEnrollment.class, commission.enrollmentIds().get(0));
        assertThat(after.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(after.getFinalGradeType()).isNull();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, commission.enrollmentIds().get(1)).getStatus())
                .isEqualTo(EnrollmentStatus.APPROVED);
        String sheet = sheetOf(director, commission);
        assertThat(rowField(sheet, commission.enrollmentIds().get(0), "enrollmentStatus")).isEqualTo("WITHDRAWN");
        assertThat(rowField(sheet, commission.enrollmentIds().get(0), "suggestedResult")).isEqualTo("PENDING");
        assertThat((Integer) JsonPath.read(sheet, "$.summary.promote")).isEqualTo(2);
    }

    private String spreadsheetTextOf(byte[] content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return StreamSupport.stream(workbook.getSheetAt(0).spliterator(), false)
                    .flatMap(row -> StreamSupport.stream(((Row) row).spliterator(), false))
                    .map(cell -> ((Cell) cell).toString())
                    .collect(Collectors.joining("|"));
        }
    }

    private String pdfTextOf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void spreadsheetAndPdfKeepAccentsAndEnyesInsideAndUseAnAsciiFileNameInTheHeader() throws Exception {
        Commission commission = commission(TERM_END_2026);
        Long admin = personWithRole("ADMINISTRATIVO");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        confirmAsTeacher(commission);
        Long closureId = closureIdOf(sheetOf(admin, commission));
        String exportPath = "/commission-closures/" + closureId + "/export";

        MvcResult draft = call(admin, get(exportPath).param("format", "xlsx"));
        assertThat(draft.getResponse().getHeader("Content-Disposition"))
                .matches("attachment; filename=\"cierre-teorias-socioculturales-\\d+-comision-unica-\\d+-borrador\\.xlsx\".*")
                .matches("\\A\\p{ASCII}*\\z");
        assertThat(spreadsheetTextOf(draft.getResponse().getContentAsByteArray()))
                .contains("Muñoz Peña", "Núñez", "Ávila", "Ibáñez", "Güemes");
        assertThat(draft.getResponse().getContentType()).contains("spreadsheetml");

        approveAs(admin, commission, APPROVE_BODY);
        MvcResult pdf = call(admin, get(exportPath).param("format", "pdf"));
        assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("-acta-ACTA-2026-0001-v1.pdf")
                .matches("\\A\\p{ASCII}*\\z");
        String text = pdfTextOf(pdf.getResponse().getContentAsByteArray());
        assertThat(text).contains("ACTA-2026-0001", "Libro 3", "Folio 12", "Muñoz Peña", "Núñez", "Ibáñez", "Güemes")
                .doesNotContain("Pagos", "Legajo");
    }

    private int remindersOf(Commission commission, int daysAfterTermEnd) {
        return jdbc.queryForObject("select count(*) from commission_closure_reminder where section_kind = 'PROGRAM' "
                + "and section_id = ? and days_after_term_end = ?", Integer.class, commission.sectionId(), daysAfterTermEnd);
    }

    @Test
    void remindersAreRecordedOnceAtThreeAndSevenDaysAfterTheTermEndEvenWhenTheJobRunsTwiceAndSkipConfirmedCommissions() throws Exception {
        Commission pending = commission(LocalDateTime.of(2026, 12, 10, 23, 59));
        Commission confirmed = commission(LocalDateTime.of(2026, 12, 10, 23, 59));
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        confirmAsTeacher(confirmed);

        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 12));
        reminderJob.remindDueSections();
        assertThat(remindersOf(pending, 3) + remindersOf(pending, 7)).isZero();

        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 13));
        reminderJob.remindDueSections();
        reminderJob.remindDueSections();
        assertThat(remindersOf(pending, 3)).isEqualTo(1);
        assertThat(remindersOf(pending, 7)).isZero();

        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 17));
        reminderJob.remindDueSections();
        reminderJob.remindDueSections();
        assertThat(remindersOf(pending, 3)).isEqualTo(1);
        assertThat(remindersOf(pending, 7)).isEqualTo(1);
        assertThat(remindersOf(confirmed, 3) + remindersOf(confirmed, 7)).isZero();
    }

    @Test
    void concurrentApprovalsNeverDuplicateNorSkipAReportNumberAndARolledBackOneReleasesIt() throws Exception {
        Clock clock = Clock.fixed(LocalDate.of(2090, 1, 5).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        ClosureReportNumberAllocator allocator = new ClosureReportNumberAllocator(jdbc, clock);
        try {
            List<Callable<String>> contenders = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                contenders.add(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    String number = allocator.allocateNextReportNumber();
                    pause();
                    return number;
                }));
            }
            List<ConcurrentRace.Outcome<String>> outcomes = ConcurrentRace.run(contenders);

            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.succeeded()).isTrue());
            assertThat(outcomes.stream().map(ConcurrentRace.Outcome::value).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder("ACTA-2090-0001", "ACTA-2090-0002", "ACTA-2090-0003", "ACTA-2090-0004",
                            "ACTA-2090-0005", "ACTA-2090-0006");
            TransactionTemplate isolated = new TransactionTemplate(transactionManager);
            isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            String discarded = isolated.execute(status -> {
                String number = allocator.allocateNextReportNumber();
                status.setRollbackOnly();
                return number;
            });
            String reused = isolated.execute(status -> allocator.allocateNextReportNumber());
            assertThat(discarded).isEqualTo("ACTA-2090-0007");
            assertThat(reused).isEqualTo("ACTA-2090-0007");
        } finally {
            committed(() -> jdbc.update("delete from closure_report_number_counter where report_year = 2090"));
        }
    }

    private void pause() {
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

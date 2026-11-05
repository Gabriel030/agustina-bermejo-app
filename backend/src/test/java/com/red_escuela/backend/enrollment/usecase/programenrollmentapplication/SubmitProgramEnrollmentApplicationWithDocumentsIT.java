package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.ApplicationDocumentFiles;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.enrollment.exception.ProgramNotAvailableException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.enrollment.exception.RequiredApplicationDocumentsMissingException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-227")
class SubmitProgramEnrollmentApplicationWithDocumentsIT extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 contenido de prueba".getBytes();
    private static final byte[] NOT_A_PDF = "this is not a pdf".getBytes();

    @Autowired private SubmitProgramEnrollmentApplicationWithDocumentsUseCase useCase;
    @Autowired private ProgramEnrollmentService programEnrollmentService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Value("${app.uploads.directory}") private String uploadsDirectory;

    private TransactionTemplate transaction;
    private Long studentId;
    private Long personId;
    private Long programId;
    private Long cohortId;
    private Long templateId;
    private Long termId;

    @BeforeEach
    void persistCommittedFixtures() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Institution institution = new Institution();
            institution.setName("Instituto atomic " + System.nanoTime());
            entityManager.persist(institution);
            String dni = String.valueOf(70000000 + (System.nanoTime() % 9999999));
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = new Program();
            program.setVisibleInCatalog(true);
            program.setName("Carrera atomic " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(0);
            entityManager.persist(program);
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
            template.setCreatedBy(person);
            entityManager.persist(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.now().minusDays(30));
            cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(person);
            entityManager.persist(cohort);
            entityManager.flush();
            studentId = student.getId();
            personId = person.getId();
            programId = program.getId();
            cohortId = cohort.getId();
            templateId = template.getId();
            termId = term.getId();
        });
    }

    @AfterEach
    void removeCommittedFixtures() {
        transaction.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from student_document where student_id = :id").setParameter("id", studentId).executeUpdate();
            entityManager.createNativeQuery("delete from program_enrollment where student_id = :id").setParameter("id", studentId).executeUpdate();
            entityManager.createNativeQuery("delete from program_enrollment_application where student_id = :id").setParameter("id", studentId).executeUpdate();
        });
        deleteStudentFiles();
    }

    private Map<DocumentType, MultipartFile> documents(byte[] dorso) {
        Map<DocumentType, MultipartFile> documents = new EnumMap<>(DocumentType.class);
        documents.put(DocumentType.DNI_FRENTE, new MockMultipartFile("dniFront", "frente.pdf", "application/pdf", PDF));
        documents.put(DocumentType.DNI_DORSO, new MockMultipartFile("dniBack", "dorso.pdf", "application/pdf", dorso));
        documents.put(DocumentType.ANALITICO_SECUNDARIO, new MockMultipartFile("academicTranscript", "analitico.pdf", "application/pdf", PDF));
        return documents;
    }

    private SubmitProgramEnrollmentApplicationWithDocumentsCommand command(Map<DocumentType, MultipartFile> documents) {
        return new SubmitProgramEnrollmentApplicationWithDocumentsCommand(studentId, programId, cohortId, new ApplicationDocumentFiles(
                documents.get(DocumentType.DNI_FRENTE), documents.get(DocumentType.DNI_DORSO), documents.get(DocumentType.ANALITICO_SECUNDARIO)));
    }

    private long applications() {
        return count("program_enrollment_application");
    }

    private long storedDocuments() {
        return count("student_document");
    }

    private long count(String table) {
        return transaction.execute(status -> ((Number) entityManager.createNativeQuery(
                "select count(*) from " + table + " where student_id = :id").setParameter("id", studentId).getSingleResult()).longValue());
    }

    private Path studentDirectory() {
        return Path.of(uploadsDirectory).resolve("students").resolve(studentId.toString());
    }

    private long filesOnDisk() {
        if (!Files.exists(studentDirectory())) {
            return 0;
        }
        try (Stream<Path> files = Files.list(studentDirectory())) {
            return files.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteStudentFiles() {
        if (!Files.exists(studentDirectory())) {
            return;
        }
        try (Stream<Path> files = Files.walk(studentDirectory())) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void studentRequestForProgramHiddenFromCatalogIsRejectedAndCreatesNothing() {
        updateProgram("visible_in_catalog = false");

        assertThatThrownBy(() -> useCase.submit(command(documents(PDF)))).isInstanceOf(ProgramNotAvailableException.class);

        assertThat(applications()).isZero();
        assertThat(storedDocuments()).isZero();
    }

    @Test
    void studentRequestForInactiveProgramIsRejectedAndCreatesNothing() {
        updateProgram("status = 'INACTIVE'");

        assertThatThrownBy(() -> useCase.submit(command(documents(PDF)))).isInstanceOf(ProgramNotAvailableException.class);

        assertThat(applications()).isZero();
    }

    @Test
    void studentRequestForActiveProgramWithNullStatusIsAccepted() {
        updateProgram("status = null");

        assertThat(useCase.submit(command(documents(PDF))).status()).isEqualTo("PENDING");
    }

    @Test
    void staffEnrollmentIntoProgramHiddenAndInactiveIsStillAllowed() {
        updateProgram("visible_in_catalog = false, status = 'INACTIVE'");

        var enrollment = transaction.execute(status -> programEnrollmentService.enroll(studentId, new CreateProgramEnrollmentRequest(programId, cohortId)));

        assertThat(enrollment.getId()).isNotNull();
    }

    private void updateProgram(String assignments) {
        transaction.executeWithoutResult(status -> entityManager.createNativeQuery(
                "update program set " + assignments + " where id = :id").setParameter("id", programId).executeUpdate());
    }

    @Test
    void submittingThreeValidDocumentsCreatesThePendingApplicationAndStoresEveryFile() {
        ProgramEnrollmentApplicationResponse application = useCase.submit(command(documents(PDF)));

        assertThat(application.status()).isEqualTo("PENDING");
        assertThat(applications()).isEqualTo(1);
        assertThat(storedDocuments()).isEqualTo(3);
        assertThat(filesOnDisk()).isEqualTo(3);
    }

    @Test
    void aFailingSecondUploadLeavesNoApplicationNoDocumentRowsAndNoOrphanFiles() {
        assertThat(applications()).isZero();

        assertThatThrownBy(() -> useCase.submit(command(documents(NOT_A_PDF))))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(applications()).isZero();
        assertThat(storedDocuments()).isZero();
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void retryingAfterAFailedUploadSucceedsInsteadOfClashingWithAnOrphanPendingApplication() {
        assertThatThrownBy(() -> useCase.submit(command(documents(NOT_A_PDF))))
                .isInstanceOf(UnsupportedFileTypeException.class);

        ProgramEnrollmentApplicationResponse retried = useCase.submit(command(documents(PDF)));

        assertThat(retried.status()).isEqualTo("PENDING");
        assertThat(applications()).isEqualTo(1);
        assertThat(storedDocuments()).isEqualTo(3);
    }

    @Test
    void aMissingFileIsRejectedBeforeAnythingIsPersisted() {
        Map<DocumentType, MultipartFile> twoOfThree = documents(PDF);
        twoOfThree.remove(DocumentType.DNI_DORSO);

        assertThatThrownBy(() -> useCase.submit(command(twoOfThree)))
                .isInstanceOf(RequiredApplicationDocumentsMissingException.class)
                .hasMessageContaining("DNI_DORSO");

        assertThat(applications()).isZero();
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void aFileOverThreeMegabytesIsRejectedBeforeAnythingIsPersisted() {
        byte[] oversized = new byte[3 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, oversized, 0, PDF.length);

        assertThatThrownBy(() -> useCase.submit(command(documents(oversized))))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.ApplicationDocumentTooLargeException.class);

        assertThat(applications()).isZero();
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void anEmptyFileCountsAsMissing() {
        Map<DocumentType, MultipartFile> withEmptyOne = documents(PDF);
        withEmptyOne.put(DocumentType.DNI_DORSO, new MockMultipartFile("dniBack", "dorso.pdf", "application/pdf", new byte[0]));

        assertThatThrownBy(() -> useCase.submit(command(withEmptyOne)))
                .isInstanceOf(RequiredApplicationDocumentsMissingException.class);

        assertThat(applications()).isZero();
    }

    @Test
    void submittingTwiceKeepsASinglePendingApplicationAndDoesNotLeaveFilesOfTheRejectedAttempt() {
        useCase.submit(command(documents(PDF)));
        long filesAfterFirst = filesOnDisk();

        assertThatThrownBy(() -> useCase.submit(command(documents(PDF))))
                .isInstanceOf(AlreadyHasPendingApplicationException.class);

        assertThat(applications()).isEqualTo(1);
        assertThat(filesOnDisk()).isEqualTo(filesAfterFirst);
    }

    @Test
    void anUnconfirmedStudentCannotSubmitAndNothingIsPersisted() {
        transaction.executeWithoutResult(status -> entityManager.createNativeQuery(
                "update student set onboarding_status = 'PROSPECTO' where id = :id").setParameter("id", studentId).executeUpdate());

        assertThatThrownBy(() -> useCase.submit(command(documents(PDF))))
                .isInstanceOf(com.red_escuela.backend.identity.exception.StudentNotConfirmedException.class);

        assertThat(applications()).isZero();
        assertThat(filesOnDisk()).isZero();
    }
}

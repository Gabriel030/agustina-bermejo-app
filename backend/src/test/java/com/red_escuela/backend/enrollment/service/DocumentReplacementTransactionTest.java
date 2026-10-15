package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.storage.FileStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DocumentReplacementTransactionTest extends AbstractIntegrationTest {
    @Autowired
    private ProspectService prospectService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long studentId;
    private Long personId;
    private Long addressId;
    private Long institutionId;

    @BeforeEach
    void persistStudent() {
        transactionTemplate.executeWithoutResult(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            String username = "doc-replacement-" + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            Institution institution = new Institution();
            institution.setName("Instituto " + username);
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            entityManager.persist(student);
            studentId = student.getId();
            personId = person.getId();
            addressId = address.getId();
            institutionId = institution.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from student_document where student_id = ?", studentId);
        jdbcTemplate.update("delete from student where id = ?", studentId);
        jdbcTemplate.update("delete from person where id = ?", personId);
        jdbcTemplate.update("delete from address where id = ?", addressId);
        jdbcTemplate.update("delete from institution where id = ?", institutionId);
    }

    private MockMultipartFile pdf(String content) {
        return new MockMultipartFile("file", "dni.pdf", "application/pdf", content.getBytes());
    }

    private String upload(String content) {
        return transactionTemplate.execute(status ->
                prospectService.addDocument(studentId, DocumentType.DNI_FRENTE, pdf(content)).getFilePath());
    }

    @Test
    void replacingADocumentDeletesThePreviousFileOnceCommitted() {
        String previous = upload("primera versión");

        String current = upload("segunda versión");

        assertThat(fileStorageService.exists(previous)).isFalse();
        assertThat(fileStorageService.exists(current)).isTrue();
    }

    @Test
    void aReplacementThatRollsBackKeepsThePreviousFileAndLeavesNoOrphan() {
        String previous = upload("primera versión");
        AtomicReference<String> attempted = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            attempted.set(prospectService.addDocument(studentId, DocumentType.DNI_FRENTE, pdf("segunda versión")).getFilePath());
            status.setRollbackOnly();
        });

        assertThat(fileStorageService.exists(previous)).isTrue();
        assertThat(fileStorageService.exists(attempted.get())).isFalse();
    }
}

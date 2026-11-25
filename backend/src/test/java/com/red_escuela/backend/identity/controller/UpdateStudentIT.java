package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.events.StudentEmailChangedEvent;
import com.red_escuela.backend.identity.repository.ContactChangeLogRepository;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class UpdateStudentIT extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ContactChangeLogRepository contactChangeLogRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private Person persistedActor;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution;
    }

    private String tokenWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        persistedActor = person;
        return jwtService.generateAccessToken(person);
    }

    private Student persistStudent(String dni, String username) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname("Original");
        person.setDni(dni);
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    private String body(String documentNumber, String email) {
        String emailField = email == null ? "" : ",\"email\":\"" + email + "\"";
        return "{\"firstName\":\"Lucía\",\"lastName\":\"Gómez\",\"documentNumber\":\"" + documentNumber
                + "\",\"birthDate\":\"2001-04-15\",\"sex\":\"F\",\"nationality\":\"Argentina\",\"phone\":\"1155550000\""
                + emailField + "}";
    }

    private List<ContactChangeLog> logsOf(Person person) {
        return contactChangeLogRepository.findAll().stream()
                .filter(log -> log.getPersonId().equals(person.getId()))
                .toList();
    }

    @Test
    void administrativeUpdatesPersonalDataAndRecordsDocumentChangeWithActor() throws Exception {
        Student student = persistStudent("30111222", "update.admin");
        String token = tokenWithRole("administrativo.update", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30999888", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Lucía"))
                .andExpect(jsonPath("$.apellido").value("Gómez"))
                .andExpect(jsonPath("$.dni").value("30999888"))
                .andExpect(jsonPath("$.sexo").value("F"))
                .andExpect(jsonPath("$.nacionalidad").value("Argentina"))
                .andExpect(jsonPath("$.telefono").value("1155550000"))
                .andExpect(jsonPath("$.birthDate").value("2001-04-15"));

        entityManager.flush();
        entityManager.clear();
        Person reloaded = entityManager.find(Person.class, student.getPerson().getId());
        assertThat(reloaded.getName()).isEqualTo("Lucía");
        assertThat(reloaded.getDni()).isEqualTo("30999888");
        List<ContactChangeLog> logs = logsOf(reloaded);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getChangeType()).isEqualTo(ContactChangeType.DOCUMENT_CHANGED);
        assertThat(logs.get(0).getActorPersonId()).isEqualTo(persistedActor.getId());
        assertThat(logs.get(0).getOldValue()).isEqualTo("DNI:30111222");
        assertThat(logs.get(0).getNewValue()).isEqualTo("DNI:30999888");
    }

    @Test
    void directorCanUpdateAStudent() throws Exception {
        Student student = persistStudent("30111223", "update.director");
        String token = tokenWithRole("director.update", "DIRECTOR");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111223", null)))
                .andExpect(status().isOk());
    }

    @Test
    void unchangedDocumentIsNotAuditedNorRejectedAsDuplicate() throws Exception {
        Student student = persistStudent("30111224", "update.same");
        String token = tokenWithRole("administrativo.same", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111224", null)))
                .andExpect(status().isOk());

        assertThat(logsOf(student.getPerson())).isEmpty();
    }

    @Test
    void preceptorCannotUpdateAStudent() throws Exception {
        Student student = persistStudent("30111225", "update.preceptor");
        String token = tokenWithRole("preceptor.update", "PRECEPTOR");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111225", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateDocumentReturnsConflictWithDomainCodeAndLeavesStudentUntouched() throws Exception {
        Student student = persistStudent("30111226", "update.dup");
        persistStudent("30555666", "update.owner");
        String token = tokenWithRole("administrativo.dup", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30555666", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_DOCUMENT_ALREADY_EXISTS"));

        assertThat(student.getPerson().getDni()).isEqualTo("30111226");
        assertThat(logsOf(student.getPerson())).isEmpty();
    }

    @Test
    void emailChangeMarksEmailUnverifiedSendsLinkToNewAddressAndNoticeToThePreviousOne() throws Exception {
        Student student = persistStudent("30111227", "update.email");
        student.getPerson().setEmailVerified(true);
        entityManager.flush();
        String token = tokenWithRole("administrativo.email", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111227", "Nuevo.Email@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo.email@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.emailEditable").value(true));

        entityManager.flush();
        entityManager.clear();
        Person reloaded = entityManager.find(Person.class, student.getPerson().getId());
        assertThat(reloaded.emailValue()).isEqualTo("nuevo.email@example.com");
        assertThat(reloaded.getEmailVerified()).isFalse();
        assertThat(applicationEvents.stream(StudentEmailChangedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.personId()).isEqualTo(reloaded.getId());
            assertThat(event.previousEmail()).isEqualTo("update.email@example.com");
            assertThat(event.newEmail()).isEqualTo("nuevo.email@example.com");
            assertThat(event.verificationLink()).contains("/verificar-email?token=");
        });
        assertThat(emailVerificationTokenRepository.findAll())
                .filteredOn(candidate -> candidate.getPerson().getId().equals(reloaded.getId()))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.getNewEmail()).isNull());
        List<ContactChangeLog> logs = logsOf(reloaded);
        assertThat(logs).extracting(ContactChangeLog::getChangeType)
                .containsExactlyInAnyOrder(ContactChangeType.EMAIL_CHANGE_REQUESTED, ContactChangeType.EMAIL_CHANGE_CONFIRMED);
        assertThat(logs).allSatisfy(entry -> {
            assertThat(entry.getActorPersonId()).isEqualTo(persistedActor.getId());
            assertThat(entry.getOldValue()).isEqualTo("update.email@example.com");
            assertThat(entry.getNewValue()).isEqualTo("nuevo.email@example.com");
        });
    }

    @Test
    void sameEmailIgnoringCaseChangesNothing() throws Exception {
        Student student = persistStudent("30111229", "update.same");
        String token = tokenWithRole("administrativo.same", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111229", "UPDATE.SAME@example.com")))
                .andExpect(status().isOk());

        assertThat(applicationEvents.stream(StudentEmailChangedEvent.class)).isEmpty();
        assertThat(logsOf(student.getPerson())).isEmpty();
    }

    @Test
    void googleAccountWithoutPasswordRejectsEmailChangeAndExposesItAsNotEditable() throws Exception {
        Student student = persistStudent("30111230", "update.google");
        student.getPerson().setAuthProvider(AuthProvider.GOOGLE);
        student.getPerson().setGoogleSubjectId("google-subject-update-google");
        entityManager.flush();
        String token = tokenWithRole("administrativo.google", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111230", "otro@example.com")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STUDENT_EMAIL_CHANGE_GOOGLE_ACCOUNT"));

        assertThat(applicationEvents.stream(StudentEmailChangedEvent.class)).isEmpty();
        assertThat(entityManager.find(Person.class, student.getPerson().getId()).emailValue())
                .isEqualTo("update.google@example.com");
    }

    @Test
    void googleAccountKeepsOtherFieldsEditableWhenEmailIsNotSent() throws Exception {
        Student student = persistStudent("30111231", "update.googleok");
        student.getPerson().setAuthProvider(AuthProvider.GOOGLE);
        student.getPerson().setGoogleSubjectId("google-subject-update-googleok");
        entityManager.flush();
        String token = tokenWithRole("administrativo.googleok", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111231", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEditable").value(false));
    }

    @Test
    void emailOwnedByAnotherPersonIsRejected() throws Exception {
        Student student = persistStudent("30111232", "update.dupemail");
        persistStudent("30111233", "update.owner");
        String token = tokenWithRole("administrativo.dupemail", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111232", "update.owner@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStudentReturnsNotFound() throws Exception {
        String token = tokenWithRole("administrativo.missing", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111228", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankFirstNameReturnsValidationError() throws Exception {
        Student student = persistStudent("30111229", "update.blank");
        String token = tokenWithRole("administrativo.blank", "ADMINISTRATIVO");

        mockMvc.perform(put("/students/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body("30111229", null).replace("Lucía", " ")))
                .andExpect(status().isBadRequest());
    }
}

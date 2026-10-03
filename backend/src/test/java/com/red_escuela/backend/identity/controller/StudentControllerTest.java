package com.red_escuela.backend.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.entity.WithdrawalReason;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.usecase.auth.LoginCommand;
import com.red_escuela.backend.identity.usecase.auth.LoginUseCase;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

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

        return jwtService.generateAccessToken(person);
    }

    private Student persistStudent(
            String dni, String username, String rawPassword, StudentOnboardingStatus onboardingStatus,
            Institution institution) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("Alumno");
        person.setLastname("De Prueba");
        person.setDni(dni);
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode(rawPassword));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        grantStudentRoleIfConfirmed(person, onboardingStatus);
        entityManager.persist(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(onboardingStatus);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    private void grantStudentRoleIfConfirmed(Person person, StudentOnboardingStatus onboardingStatus) {
        if (onboardingStatus == StudentOnboardingStatus.CONFIRMADO) {
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        }
    }

    @Test
    void listingWithoutPreceptorOrAdministrativeRoleReturns403() throws Exception {
        String token = tokenWithRole("alumno1", "ALUMNO");

        mockMvc.perform(get("/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void directorCanListAndViewRecordButCannotWithdraw() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent(
                "42111222", "42111222", "correcta123", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("director1", "DIRECTOR");

        mockMvc.perform(get("/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/students/{id}", student.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        WithdrawStudentRequest request = new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "motivo");
        mockMvc.perform(post("/students/{id}/withdraw", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void billingOverviewWithNonExistentStudentReturns404() throws Exception {
        String token = tokenWithRole("administrativo.billing1", "ADMINISTRATIVO");

        mockMvc.perform(get("/students/{id}/billing-overview", 999999L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingWithoutFilterReturnsOnlyConfirmed() throws Exception {
        Institution institution = persistInstitution();
        persistStudent("40111222", "40111222", "correcta123", StudentOnboardingStatus.CONFIRMADO, institution);
        persistStudent("40333444", "prospecto-sin-username", "irrelevante", StudentOnboardingStatus.PROSPECTO, institution);
        String token = tokenWithRole("preceptor1", "PRECEPTOR");

        mockMvc.perform(get("/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dni=='40111222')].onboardingStatus").value("CONFIRMADO"))
                .andExpect(jsonPath("$[?(@.dni=='40333444')]").isEmpty());
    }

    @Test
    void listingWithExplicitOnboardingStatusReturnsProspects() throws Exception {
        Institution institution = persistInstitution();
        persistStudent("40555666", "40555666", "correcta123", StudentOnboardingStatus.CONFIRMADO, institution);
        persistStudent("40777888", "prospecto2", "irrelevante", StudentOnboardingStatus.PROSPECTO, institution);
        String token = tokenWithRole("preceptor2", "PRECEPTOR");

        mockMvc.perform(get("/students")
                        .param("onboardingStatus", "PROSPECTO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dni=='40777888')].onboardingStatus").value("PROSPECTO"))
                .andExpect(jsonPath("$[?(@.dni=='40555666')]").isEmpty());
    }

    @Test
    void withdrawMarksStatusDeletedSavesReasonAndBlocksLaterLogin() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent(
                "40999000", "40999000", "correcta123", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("preceptor3", "PRECEPTOR");

        WithdrawStudentRequest request = new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "dejó de asistir");

        mockMvc.perform(post("/students/{id}/withdraw", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        Student reloaded = entityManager.find(Student.class, student.getId());
        assertThat(reloaded.getPerson().getStatus()).isEqualTo(StatusType.DELETED);
        assertThat(reloaded.getWithdrawalReason()).isEqualTo(WithdrawalReason.ABANDONO);
        assertThat(reloaded.getWithdrawalDetail()).isEqualTo("dejó de asistir");

        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("40999000", "correcta123", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private Student persistStudentWithName(
            String dni, String username, String name, String lastname,
            StudentOnboardingStatus onboardingStatus, Institution institution) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname(lastname);
        person.setDni(dni);
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        grantStudentRoleIfConfirmed(person, onboardingStatus);
        entityManager.persist(person);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(onboardingStatus);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    @Test
    void searchByNameFiltersCorrectly() throws Exception {
        Institution institution = persistInstitution();
        persistStudentWithName("41100001", "garcia.juan", "Juan", "Garcia", StudentOnboardingStatus.CONFIRMADO, institution);
        persistStudentWithName("41100002", "perez.maria", "Maria", "Perez", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("preceptor.search1", "PRECEPTOR");

        mockMvc.perform(get("/students").param("search", "garcia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dni=='41100001')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.dni=='41100002')]").isEmpty());
    }

    @Test
    void searchByDniFiltersCorrectly() throws Exception {
        Institution institution = persistInstitution();
        persistStudent("41200001", "alumno.dni1", "pass", StudentOnboardingStatus.CONFIRMADO, institution);
        persistStudent("41200002", "alumno.dni2", "pass", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("preceptor.search2", "PRECEPTOR");

        mockMvc.perform(get("/students").param("search", "41200001").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dni=='41200001')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.dni=='41200002')]").isEmpty());
    }

    @Test
    void recordReturnsExtendedData() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("41300001", "alumno.record1", "pass", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("preceptor.record1", "PRECEPTOR");

        mockMvc.perform(get("/students/{id}", student.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId()))
                .andExpect(jsonPath("$.dni").value("41300001"))
                .andExpect(jsonPath("$.onboardingStatus").value("CONFIRMADO"));
    }

    @Test
    void documentsWithNonExistentStudentReturns404() throws Exception {
        String token = tokenWithRole("preceptor.docs1", "PRECEPTOR");

        mockMvc.perform(get("/students/{id}/documents", 999888L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void documentsWithStudentWithoutDocumentsReturnsEmptyList() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("41400001", "alumno.docs1", "pass", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("preceptor.docs2", "PRECEPTOR");

        mockMvc.perform(get("/students/{id}/documents", student.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void resetPasswordGeneratesTokenWithoutExposingThePassword() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent(
                "41000111", "41000111", "correcta123", StudentOnboardingStatus.CONFIRMADO, institution);
        String token = tokenWithRole("preceptor4", "PRECEPTOR");

        mockMvc.perform(post("/students/{id}/reset-password", student.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getPerson().getId()).isEqualTo(student.getPerson().getId());
    }
}

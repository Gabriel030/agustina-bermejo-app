package com.red_escuela.backend.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffControllerTest extends AbstractIntegrationTest {

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
    private PersonRepository personRepository;

    @Autowired
    private InstructorRepository instructorRepository;

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

    private CreateStaffRequest staffRequest(String email, String dni, String role) {
        return new CreateStaffRequest(
                "Nombre", "Apellido", email, dni, null,
                "Calle", "1", null, "Ciudad", null, null, null, role);
    }

    @Test
    void creatingTeacherCreatesPersonAndInstructorAndAssignsRole() throws Exception {
        String token = tokenWithRole("admin1", "ADMINISTRATIVO");
        CreateStaffRequest request = staffRequest("docente.nuevo@example.com", "50111222", "DOCENTE");

        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCENTE"))
                .andReturn().getResponse().getContentAsString();

        Long personId = objectMapper.readTree(body).get("id").asLong();
        Person person = personRepository.findById(personId).orElseThrow();
        assertThat(person.getRoles()).extracting(Role::getName).contains("DOCENTE");
        assertThat(instructorRepository.findAll())
                .anyMatch(instructor -> instructor.getPerson().getId().equals(personId));
    }

    @Test
    void creatingPreceptorDoesNotCreateInstructor() throws Exception {
        String token = tokenWithRole("admin2", "ADMINISTRATIVO");
        CreateStaffRequest request = staffRequest("preceptor.nuevo@example.com", "50333444", "PRECEPTOR");

        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long personId = objectMapper.readTree(body).get("id").asLong();
        assertThat(instructorRepository.findAll())
                .noneMatch(instructor -> instructor.getPerson().getId().equals(personId));
    }

    @Test
    void creatingWithInvalidRoleReturns400() throws Exception {
        String token = tokenWithRole("admin3", "ADMINISTRATIVO");
        CreateStaffRequest request = staffRequest("rol.invalido@example.com", "50555666", "DIRECTOR");

        mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingStaffWithoutAdministrativeRoleReturns403() throws Exception {
        String adminToken = tokenWithRole("admin4", "ADMINISTRATIVO");
        CreateStaffRequest createRequest = staffRequest("editable@example.com", "50777888", "DOCENTE");
        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long personId = objectMapper.readTree(body).get("id").asLong();

        String nonAdminToken = tokenWithRole("preceptor-no-admin", "PRECEPTOR");
        UpdateStaffRequest updateRequest = new UpdateStaffRequest(
                "nuevo@example.com", "1199998888", null, null, null, null, null, null, null);

        mockMvc.perform(put("/staff/{id}", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + nonAdminToken)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingStaffUpdatesContactAndNeverTheDni() throws Exception {
        String adminToken = tokenWithRole("admin5", "ADMINISTRATIVO");
        CreateStaffRequest createRequest = staffRequest("editable2@example.com", "50999000", "DOCENTE");
        String body = mockMvc.perform(post("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long personId = objectMapper.readTree(body).get("id").asLong();

        UpdateStaffRequest updateRequest = new UpdateStaffRequest(
                "actualizado@example.com", "1199998888", "Nueva Calle", "2", null, "Otra Ciudad", null, null, null);

        mockMvc.perform(put("/staff/{id}", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("editable2@example.com"))
                .andExpect(jsonPath("$.dni").value("50999000"));
    }

    @Test
    void staffRecordExposesEmailVerificationAndProviderButNoSecrets() throws Exception {
        String token = tokenWithRole("director.record", "DIRECTOR");
        Address address = new Address();
        entityManager.persist(address);
        Person teacher = new Person();
        teacher.setUsername("verified.teacher");
        teacher.setEmail("verified.teacher@example.com");
        teacher.setAddress(address);
        teacher.setStatus(StatusType.ACTIVE);
        teacher.setEmailVerified(true);
        teacher.getRoles().add(roleRepository.findByName("DOCENTE").orElseThrow());
        entityManager.persist(teacher);
        entityManager.flush();

        mockMvc.perform(get("/staff/{id}", teacher.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/staff").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id==" + teacher.getId() + ")].status").value("ACTIVE"));
    }

    @AfterEach
    void restoreClock() {
        ApplicationTime.useClock(Clock.systemDefaultZone());
    }

    private Person lockedTeacher(String username, LocalDateTime lockedUntil, int failedAttempts) {
        Address address = new Address();
        entityManager.persist(address);
        Person teacher = new Person();
        teacher.setUsername(username);
        teacher.setEmail(username + "@example.com");
        teacher.setAddress(address);
        teacher.setStatus(StatusType.ACTIVE);
        teacher.setLockedUntil(lockedUntil);
        teacher.setFailedLoginAttempts(failedAttempts);
        teacher.getRoles().add(roleRepository.findByName("DOCENTE").orElseThrow());
        entityManager.persist(teacher);
        entityManager.flush();
        return teacher;
    }

    private void freezeClockAt(LocalDateTime now) {
        ZoneId zone = ZoneId.systemDefault();
        ApplicationTime.useClock(Clock.fixed(now.atZone(zone).toInstant(), zone));
    }

    @Test
    void directorSeesActiveLockAndFailedAttemptsOfStaffMember() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 10, 0);
        freezeClockAt(now);
        String token = tokenWithRole("director.lock", "DIRECTOR");
        Person teacher = lockedTeacher("locked.teacher", now.plusMinutes(10), 5);

        mockMvc.perform(get("/staff/{id}", teacher.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").value("2026-11-04T10:10:00"))
                .andExpect(jsonPath("$.failedLoginAttempts").value(5))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void administrativeSeesActiveLockButNeverFailedAttempts() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 10, 0);
        freezeClockAt(now);
        String token = tokenWithRole("admin.lock", "ADMINISTRATIVO");
        Person teacher = lockedTeacher("locked.teacher2", now.plusMinutes(10), 5);

        mockMvc.perform(get("/staff/{id}", teacher.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").value("2026-11-04T10:10:00"))
                .andExpect(jsonPath("$.failedLoginAttempts").doesNotExist());
    }

    @Test
    void expiredLockIsNotExposed() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 10, 0);
        freezeClockAt(now);
        String token = tokenWithRole("director.expired", "DIRECTOR");
        Person teacher = lockedTeacher("expired.teacher", now.minusMinutes(1), 3);

        mockMvc.perform(get("/staff/{id}", teacher.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.failedLoginAttempts").value(3));
    }

    @Test
    void lockExpiringExactlyNowIsNotExposed() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 10, 0);
        freezeClockAt(now);
        String token = tokenWithRole("director.boundary", "DIRECTOR");
        Person teacher = lockedTeacher("boundary.teacher", now, 0);

        mockMvc.perform(get("/staff/{id}", teacher.getId()).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }
}

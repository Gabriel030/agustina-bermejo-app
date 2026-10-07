package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.PasswordResetCodeIssuedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class StaffCreationCharacterizationTest extends AccessCharacterizationSupport {

    @Autowired
    private ApplicationEvents applicationEvents;

    private ResultActions createStaff(Person actor, String dni, String role) throws Exception {
        CreateStaffRequest request = new CreateStaffRequest("Nuevo", "Personal", "staff." + dni + "@example.com",
                dni, "Calle", "1", null, "Ciudad", "Provincia", "1000", "AR", role);
        return mockMvc.perform(post("/staff")
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    void administrativeCreatesATeacherWithDniAsUsernameNoPasswordAndAResetCodeEvent() throws Exception {
        Person admin = persistPerson("access.admin1", "70000021", "ADMINISTRATIVO");

        createStaff(admin, "70000101", "DOCENTE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCENTE"))
                .andExpect(jsonPath("$.dni").value("70000101"))
                .andExpect(jsonPath("$.username").value("70000101"))
                .andExpect(jsonPath("$.email").value("staff.70000101@example.com"));

        Person created = entityManager.createQuery("select p from Person p where p.dni = '70000101'", Person.class)
                .getSingleResult();
        assertThat(created.getPassword()).isNull();
        assertThat(created.getRoles()).extracting("name").containsExactly("DOCENTE");
        assertThat(countRows("Instructor", "e.person.id = " + created.getId())).isEqualTo(1);
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(created.getId())).count()).isEqualTo(1);
        login("70000101", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void administrativeCreatesAPreceptorWithoutCreatingAnInstructor() throws Exception {
        Person admin = persistPerson("access.admin2", "70000022", "ADMINISTRATIVO");

        createStaff(admin, "70000102", "PRECEPTOR")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PRECEPTOR"));

        Person created = entityManager.createQuery("select p from Person p where p.dni = '70000102'", Person.class)
                .getSingleResult();
        assertThat(countRows("Instructor", "e.person.id = " + created.getId())).isZero();
    }

    @Test
    void administrativeCannotCreateAnotherAdministrative() throws Exception {
        Person admin = persistPerson("access.admin3", "70000023", "ADMINISTRATIVO");

        createStaff(admin, "70000103", "ADMINISTRATIVO").andExpect(status().isForbidden());

        assertThat(countRows("Person", "e.dni = '70000103'")).isZero();
    }

    @Test
    void directorCreatesAnAdministrative() throws Exception {
        Person director = persistPerson("access.director1", "70000024", "DIRECTOR");

        createStaff(director, "70000104", "ADMINISTRATIVO")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMINISTRATIVO"));
    }

    @Test
    void directorCannotCreateTeachersOrPreceptors() throws Exception {
        Person director = persistPerson("access.director2", "70000025", "DIRECTOR");

        createStaff(director, "70000105", "DOCENTE").andExpect(status().isForbidden());
        createStaff(director, "70000106", "PRECEPTOR").andExpect(status().isForbidden());

        assertThat(countRows("Person", "e.dni in ('70000105', '70000106')")).isZero();
    }

    @Test
    void directorRoleIsNotACreatableStaffRoleAndReturns400() throws Exception {
        Person director = persistPerson("access.director3", "70000026", "DIRECTOR");

        createStaff(director, "70000107", "DIRECTOR").andExpect(status().isBadRequest());
    }

    @Test
    void teacherStudentAndPreceptorCannotCreateStaff() throws Exception {
        Person teacher = persistPerson("access.teacher1", "70000027", "DOCENTE");
        Person student = persistPerson("access.student1", "70000028", "ALUMNO");
        Person preceptor = persistPerson("access.preceptor1", "70000029", "PRECEPTOR");

        createStaff(teacher, "70000108", "DOCENTE").andExpect(status().isForbidden());
        createStaff(student, "70000109", "DOCENTE").andExpect(status().isForbidden());
        createStaff(preceptor, "70000110", "DOCENTE").andExpect(status().isForbidden());
    }

    @Test
    void creatingStaffWithAnExistingDniReturns409() throws Exception {
        Person admin = persistPerson("access.admin4", "70000030", "ADMINISTRATIVO");
        createStaff(admin, "70000111", "DOCENTE").andExpect(status().isCreated());

        createStaff(admin, "70000111", "PRECEPTOR").andExpect(status().isConflict());
    }

    @Test
    void createdStaffCompletesTheFirstAccessThroughTheResetCodeFlow() throws Exception {
        Person admin = persistPerson("access.admin5", "70000031", "ADMINISTRATIVO");
        createStaff(admin, "70000112", "PRECEPTOR").andExpect(status().isCreated());
        Person created = entityManager.createQuery("select p from Person p where p.dni = '70000112'", Person.class)
                .getSingleResult();
        String code = applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(created.getId()))
                .findFirst().orElseThrow().code();

        String body = postJson("/auth/verify-reset-code",
                new com.red_escuela.backend.identity.dto.VerifyResetCodeRequest(created.emailValue(), code))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String resetToken = objectMapper.readTree(body).get("resetToken").asText();
        postJson("/auth/reset-password",
                new com.red_escuela.backend.identity.dto.ResetPasswordRequest(resetToken, "PrimerClave123"))
                .andExpect(status().isNoContent());

        login("70000112", "PrimerClave123").andExpect(status().isOk());
    }
}

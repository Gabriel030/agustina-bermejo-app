package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.dto.ForgotPasswordRequest;
import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.service.EmailChangeRequestedEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class EmailChangeReverificationCharacterizationTest extends AccessCharacterizationSupport {

    @Autowired
    private ApplicationEvents applicationEvents;

    private ResultActions updateOwnProfile(Person person, String email, String dni, String currentPassword)
            throws Exception {
        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(email, null, null, dni, null, null, null, null,
                null, null, null, null, currentPassword);
        return mockMvc.perform(put("/me")
                .header("Authorization", bearer(person))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions updateStaffEmail(Person actor, Person target, String email) throws Exception {
        UpdateStaffRequest request = new UpdateStaffRequest(email, null, null, null, null, null, null, null, null);
        return mockMvc.perform(put("/staff/" + target.getId())
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions resetStaffPassword(Person actor, Person target) throws Exception {
        return mockMvc.perform(post("/staff/" + target.getId() + "/reset-password")
                .header("Authorization", bearer(actor)));
    }

    private EmailChangeRequestedEvent lastEmailChangeEvent(Person person) {
        return applicationEvents.stream(EmailChangeRequestedEvent.class)
                .filter(event -> event.personId().equals(person.getId()))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private String tokenOf(EmailChangeRequestedEvent event) {
        return event.verificationLink().substring(event.verificationLink().indexOf("token=") + 6);
    }

    private List<ContactChangeLog> logOf(Person person) {
        entityManager.flush();
        return entityManager.createQuery("select l from ContactChangeLog l where l.personId = :id order by l.id",
                ContactChangeLog.class).setParameter("id", person.getId()).getResultList();
    }

    private Person reload(Person person) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Person.class, person.getId());
    }

    @Test
    void emailChangeWithAWrongCurrentPasswordIsRejectedAndNothingChanges() throws Exception {
        Person person = persistPerson("access.emailchg1", "70000201", "ALUMNO");

        updateOwnProfile(person, "otro1@example.com", null, "Equivocada1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(reload(person).emailValue()).isEqualTo("access.emailchg1@example.com");
        assertThat(applicationEvents.stream(EmailChangeRequestedEvent.class).count()).isZero();
        assertThat(logOf(person)).isEmpty();
    }

    @Test
    void emailChangeWithoutCurrentPasswordIsRejected() throws Exception {
        Person person = persistPerson("access.emailchg2", "70000202", "ALUMNO");

        updateOwnProfile(person, "otro2@example.com", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(reload(person).emailValue()).isEqualTo("access.emailchg2@example.com");
    }

    @Test
    void emailChangeStaysPendingNotifiesTheOldAddressAndKeepsTheCurrentEmail() throws Exception {
        Person person = persistPerson("access.emailchg3", "70000203", "ALUMNO");

        updateOwnProfile(person, "nuevo3@example.com", null, PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/me").header("Authorization", bearer(person)))
                .andExpect(jsonPath("$.email").value("access.emailchg3@example.com"));
        EmailChangeRequestedEvent event = lastEmailChangeEvent(person);
        assertThat(event.previousEmail()).isEqualTo("access.emailchg3@example.com");
        assertThat(event.newEmail()).isEqualTo("nuevo3@example.com");
        List<ContactChangeLog> log = logOf(person);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getChangeType()).isEqualTo(ContactChangeType.EMAIL_CHANGE_REQUESTED);
        assertThat(log.get(0).getActorPersonId()).isEqualTo(person.getId());
        assertThat(log.get(0).getNewValue()).isEqualTo("nuevo3@example.com");
    }

    @Test
    void pendingEmailReceivesNoPasswordResetWhileTheCurrentOneDoes() throws Exception {
        Person person = persistPerson("access.emailchg4", "70000204", "ALUMNO");
        updateOwnProfile(person, "nuevo4@example.com", null, PASSWORD).andExpect(status().isOk());

        postJson("/auth/forgot-password", new ForgotPasswordRequest("nuevo4@example.com"))
                .andExpect(status().isAccepted());
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(person.getId())).count()).isZero();

        postJson("/auth/forgot-password", new ForgotPasswordRequest("access.emailchg4@example.com"))
                .andExpect(status().isAccepted());
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(person.getId())).count()).isEqualTo(1);
    }

    @Test
    void confirmingTheLinkActivatesTheNewEmailAndMarksItVerified() throws Exception {
        Person person = persistPerson("access.emailchg5", "70000205", "ALUMNO");
        updateOwnProfile(person, "nuevo5@example.com", null, PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/verify-email").param("token", tokenOf(lastEmailChangeEvent(person))))
                .andExpect(status().isOk());

        Person confirmed = reload(person);
        assertThat(confirmed.emailValue()).isEqualTo("nuevo5@example.com");
        assertThat(confirmed.getEmailVerified()).isTrue();
        assertThat(logOf(person)).extracting(ContactChangeLog::getChangeType)
                .containsExactly(ContactChangeType.EMAIL_CHANGE_REQUESTED, ContactChangeType.EMAIL_CHANGE_CONFIRMED);
        postJson("/auth/forgot-password", new ForgotPasswordRequest("nuevo5@example.com"))
                .andExpect(status().isAccepted());
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(person.getId())).count()).isEqualTo(1);
    }

    @Test
    void sameEmailWithoutPasswordIsNotAChange() throws Exception {
        Person person = persistPerson("access.emailchg6", "70000206", "ALUMNO");

        updateOwnProfile(person, "access.emailchg6@example.com", "70000206", null).andExpect(status().isOk());

        assertThat(applicationEvents.stream(EmailChangeRequestedEvent.class).count()).isZero();
        assertThat(logOf(person)).isEmpty();
    }

    @Test
    void documentChangeRequiresTheCurrentPasswordAndIsAudited() throws Exception {
        Person person = persistPerson("access.emailchg7", "70000207", "ALUMNO");

        updateOwnProfile(person, null, "70009999", "Equivocada1").andExpect(status().isBadRequest());
        assertThat(reload(person).getDni()).isEqualTo("70000207");

        updateOwnProfile(person, null, "70009999", PASSWORD).andExpect(status().isOk());

        assertThat(reload(person).getDni()).isEqualTo("70009999");
        List<ContactChangeLog> log = logOf(person);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getChangeType()).isEqualTo(ContactChangeType.DOCUMENT_CHANGED);
        assertThat(log.get(0).getOldValue()).isEqualTo("DNI:70000207");
        assertThat(log.get(0).getNewValue()).isEqualTo("DNI:70009999");
    }

    @Test
    void administrativeEmailChangeOfATeacherStaysPendingIsAuditedWithTheActorAndBlocksTheReset() throws Exception {
        Person admin = persistPerson("access.emailchg8a", "70000208", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg8b", "70000209", "DOCENTE");

        updateStaffEmail(admin, teacher, "docente.nuevo@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("access.emailchg8b@example.com"));

        EmailChangeRequestedEvent event = lastEmailChangeEvent(teacher);
        assertThat(event.previousEmail()).isEqualTo("access.emailchg8b@example.com");
        assertThat(event.newEmail()).isEqualTo("docente.nuevo@example.com");
        List<ContactChangeLog> log = logOf(teacher);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getActorPersonId()).isEqualTo(admin.getId());
        resetStaffPassword(admin, teacher).andExpect(status().isConflict());
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(issued -> issued.personId().equals(teacher.getId())).count()).isZero();
    }

    @Test
    void staffResetStaysBlockedForTwentyFourHoursAfterConfirmationThenWorks() throws Exception {
        Person admin = persistPerson("access.emailchg9a", "70000210", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg9b", "70000211", "DOCENTE");
        updateStaffEmail(admin, teacher, "docente9.nuevo@example.com").andExpect(status().isOk());
        mockMvc.perform(get("/verify-email").param("token", tokenOf(lastEmailChangeEvent(teacher))))
                .andExpect(status().isOk());

        resetStaffPassword(admin, teacher).andExpect(status().isConflict());

        ApplicationTime.useClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(25)));
        try {
            resetStaffPassword(admin, teacher).andExpect(status().is2xxSuccessful());
        } finally {
            ApplicationTime.useClock(Clock.systemDefaultZone());
        }
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(issued -> issued.personId().equals(teacher.getId())).count()).isEqualTo(1);
    }

    @Test
    void staffResetWithoutAnyEmailChangeKeepsWorking() throws Exception {
        Person admin = persistPerson("access.emailchg10a", "70000212", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg10b", "70000213", "DOCENTE");

        resetStaffPassword(admin, teacher).andExpect(status().is2xxSuccessful());
    }

    @Test
    void staffEmailChangeToAnExistingAddressIsRejected() throws Exception {
        Person admin = persistPerson("access.emailchg11a", "70000214", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg11b", "70000215", "DOCENTE");

        updateStaffEmail(admin, teacher, "access.emailchg11a@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void firstTimeDocumentLoadNeedsNoPassword() throws Exception {
        Person person = persistPerson("access.emailchg12", "70000216", "ALUMNO");
        person.setDni(null);
        entityManager.flush();
        entityManager.clear();

        updateOwnProfile(person, null, "70009998", null).andExpect(status().isOk());

        assertThat(reload(person).getDni()).isEqualTo("70009998");
    }

    @Test
    void changingAnExistingDocumentWithoutPasswordIsRejected() throws Exception {
        Person person = persistPerson("access.emailchg13", "70000217", "ALUMNO");

        updateOwnProfile(person, null, "70009997", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(reload(person).getDni()).isEqualTo("70000217");
    }
}

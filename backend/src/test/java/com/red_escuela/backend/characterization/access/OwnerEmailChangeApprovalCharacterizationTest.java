package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.dto.EmailChangeTokenRequest;
import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.specialist.EmailChangeOwnerApprovalRequestedEvent;
import com.red_escuela.backend.identity.specialist.EmailChangeRequestedEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
@Tag("T955")
class OwnerEmailChangeApprovalCharacterizationTest extends AccessCharacterizationSupport {

    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    private void staffRequestsChange(Person admin, Person target, String email) throws Exception {
        UpdateStaffRequest request = new UpdateStaffRequest(email, null, null, null, null, null, null, null, null);
        mockMvc.perform(put("/staff/" + target.getId())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String ownerTokenOf(Person target) {
        EmailChangeOwnerApprovalRequestedEvent event = applicationEvents.stream(EmailChangeOwnerApprovalRequestedEvent.class)
                .filter(candidate -> candidate.personId().equals(target.getId()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(tokenFromLink(event.approvalLink())).isEqualTo(tokenFromLink(event.cancellationLink()));
        return tokenFromLink(event.cancellationLink());
    }

    private String tokenFromLink(String link) {
        return link.substring(link.indexOf("token=") + 6);
    }

    private ResultActions postToken(String path, String token) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EmailChangeTokenRequest(token))));
    }

    private long verificationEventsFor(Person person) {
        return applicationEvents.stream(EmailChangeRequestedEvent.class)
                .filter(event -> event.personId().equals(person.getId())).count();
    }

    private Person reload(Person person) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Person.class, person.getId());
    }

    private EmailVerificationToken pendingTokenOf(Person person) {
        entityManager.flush();
        return tokenRepository.findAll().stream().filter(token -> token.getPerson().getId().equals(person.getId())).findFirst().orElseThrow();
    }

    @Test
    void ownerApprovalSendsVerificationToNewAddress() throws Exception {
        Person admin = persistPerson("access.owner1a", "70000301", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner1b", "70000302", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner1@example.com");
        String ownerToken = ownerTokenOf(teacher);
        assertThat(verificationEventsFor(teacher)).isZero();

        postToken("/approve-email-change", ownerToken).andExpect(status().isOk());

        assertThat(applicationEvents.stream(EmailChangeRequestedEvent.class).filter(event -> event.personId().equals(teacher.getId())))
                .singleElement().satisfies(event -> {
                    assertThat(event.newEmail()).isEqualTo("docente.owner1@example.com");
                    assertThat(event.verificationLink()).contains("/verificar-email?token=");
                    assertThat(event.previousEmail()).isNull();
                });
        EmailVerificationToken token = pendingTokenOf(teacher);
        assertThat(token.isAwaitingOwnerApproval()).isFalse();
        assertThat(token.getOwnerApprovedAt()).isNotNull();
    }

    @Test
    void approvedVerificationLinkConfirmsTheNewEmail() throws Exception {
        Person admin = persistPerson("access.owner2a", "70000303", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner2b", "70000304", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner2@example.com");
        postToken("/approve-email-change", ownerTokenOf(teacher)).andExpect(status().isOk());
        EmailChangeRequestedEvent event = applicationEvents.stream(EmailChangeRequestedEvent.class)
                .filter(candidate -> candidate.personId().equals(teacher.getId())).findFirst().orElseThrow();

        mockMvc.perform(get("/verify-email")
                .param("token", tokenFromLink(event.verificationLink()))).andExpect(status().isOk());

        assertThat(reload(teacher).emailValue()).isEqualTo("docente.owner2@example.com");
    }

    @Test
    void ownerCancelDiscardsRequest() throws Exception {
        Person admin = persistPerson("access.owner3a", "70000305", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner3b", "70000306", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner3@example.com");
        String ownerToken = ownerTokenOf(teacher);

        postToken("/cancel-email-change", ownerToken).andExpect(status().isOk());

        entityManager.flush();
        assertThat(tokenRepository.findAll().stream().filter(token -> token.getPerson().getId().equals(teacher.getId()))).isEmpty();
        postToken("/approve-email-change", ownerToken).andExpect(status().isBadRequest());
        assertThat(verificationEventsFor(teacher)).isZero();
    }

    @Test
    void approvalCannotBeReplayed() throws Exception {
        Person admin = persistPerson("access.owner4a", "70000307", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner4b", "70000308", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner4@example.com");
        String ownerToken = ownerTokenOf(teacher);

        postToken("/approve-email-change", ownerToken).andExpect(status().isOk());
        postToken("/approve-email-change", ownerToken).andExpect(status().isBadRequest());

        assertThat(verificationEventsFor(teacher)).isEqualTo(1);
    }

    @Test
    void approvalTokenRejectedWhenExpiredOrUsed() throws Exception {
        Person admin = persistPerson("access.owner5a", "70000309", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner5b", "70000310", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner5@example.com");
        String ownerToken = ownerTokenOf(teacher);
        pendingTokenOf(teacher).setExpiresAt(ApplicationTime.now().minus(Duration.ofMinutes(1)));
        entityManager.flush();

        postToken("/email-change-preview", ownerToken).andExpect(status().isBadRequest());
        postToken("/approve-email-change", ownerToken).andExpect(status().isBadRequest());
        postToken("/approve-email-change", "x".repeat(43)).andExpect(status().isBadRequest());

        assertThat(verificationEventsFor(teacher)).isZero();
        assertThat(pendingTokenOf(teacher).isAwaitingOwnerApproval()).isTrue();
    }

    @Test
    void previewDoesNotConsumeTheToken() throws Exception {
        Person admin = persistPerson("access.owner6a", "70000311", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner6b", "70000312", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner6@example.com");
        String ownerToken = ownerTokenOf(teacher);

        postToken("/email-change-preview", ownerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentEmailMasked").value("ac***@example.com"))
                .andExpect(jsonPath("$.newEmailMasked").value("do***@example.com"))
                .andExpect(jsonPath("$.requestedByRole").value("ADMINISTRATIVO"))
                .andExpect(jsonPath("$.expiresAt").exists());
        postToken("/email-change-preview", ownerToken).andExpect(status().isOk());

        assertThat(pendingTokenOf(teacher).isAwaitingOwnerApproval()).isTrue();
        postToken("/approve-email-change", ownerToken).andExpect(status().isOk());
    }

    @Test
    void previewRejectsAnUnknownToken() throws Exception {
        postToken("/email-change-preview", "y".repeat(43)).andExpect(status().isBadRequest());
    }

    @Test
    void legacyCancelLinkStillCancels() throws Exception {
        Person teacher = persistPerson("access.owner7b", "70000314", "DOCENTE");
        updateOwnEmail(teacher, "docente.owner7@example.com");
        EmailChangeRequestedEvent event = applicationEvents.stream(EmailChangeRequestedEvent.class)
                .filter(candidate -> candidate.personId().equals(teacher.getId())).findFirst().orElseThrow();

        postToken("/cancel-email-change", tokenFromLink(event.cancellationLink())).andExpect(status().isOk());

        entityManager.flush();
        assertThat(tokenRepository.findAll().stream().filter(token -> token.getPerson().getId().equals(teacher.getId()))).isEmpty();
    }

    private void updateOwnEmail(Person person, String email) throws Exception {
        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(
                email, null, null, null, null, null, null, null, null, null, null, null, PASSWORD);
        mockMvc.perform(put("/me").header("Authorization", bearer(person)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());
    }

    @Test
    void cancelAfterApprovalStillDiscardsTheRequest() throws Exception {
        Person admin = persistPerson("access.owner8a", "70000315", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner8b", "70000316", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner8@example.com");
        String ownerToken = ownerTokenOf(teacher);
        postToken("/approve-email-change", ownerToken).andExpect(status().isOk());

        postToken("/cancel-email-change", ownerToken).andExpect(status().isOk());

        entityManager.flush();
        assertThat(tokenRepository.findAll().stream().filter(token -> token.getPerson().getId().equals(teacher.getId()))).isEmpty();
        postToken("/cancel-email-change", ownerToken).andExpect(status().isBadRequest());
    }

    @Test
    void previewOfAnAlreadyApprovedOrOwnChangeIsRejected() throws Exception {
        Person admin = persistPerson("access.owner9a", "70000317", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner9b", "70000318", "DOCENTE");
        Person other = persistPerson("access.owner9c", "70000319", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner9@example.com");
        String ownerToken = ownerTokenOf(teacher);
        postToken("/approve-email-change", ownerToken).andExpect(status().isOk());
        updateOwnEmail(other, "docente.owner9c@example.com");
        EmailChangeRequestedEvent ownChange = applicationEvents.stream(EmailChangeRequestedEvent.class)
                .filter(candidate -> candidate.personId().equals(other.getId())).findFirst().orElseThrow();

        postToken("/email-change-preview", ownerToken).andExpect(status().isBadRequest());
        postToken("/email-change-preview", tokenFromLink(ownChange.cancellationLink())).andExpect(status().isBadRequest());
        postToken("/approve-email-change", tokenFromLink(ownChange.cancellationLink())).andExpect(status().isBadRequest());
    }

    @Test
    void approvalGivesTheNewAddressAFullVerificationWindow() throws Exception {
        Person admin = persistPerson("access.owner10a", "70000320", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.owner10b", "70000321", "DOCENTE");
        staffRequestsChange(admin, teacher, "docente.owner10@example.com");
        String ownerToken = ownerTokenOf(teacher);
        pendingTokenOf(teacher).setExpiresAt(ApplicationTime.now().plusMinutes(5));
        entityManager.flush();

        postToken("/approve-email-change", ownerToken).andExpect(status().isOk());

        assertThat(pendingTokenOf(teacher).getExpiresAt()).isAfter(ApplicationTime.now().plusHours(47));
    }
}

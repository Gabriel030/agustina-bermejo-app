package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.dto.CancelEmailChangeRequest;
import com.red_escuela.backend.identity.dto.ForgotPasswordRequest;
import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.specialist.EmailChangeOwnerApprovalRequestedEvent;
import com.red_escuela.backend.identity.specialist.OwnerEmailChangeApprover;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.specialist.EmailChangeRequestedEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
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
    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired
    private OwnerEmailChangeApprover ownerEmailChangeApprover;

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

    private EmailChangeOwnerApprovalRequestedEvent lastOwnerApprovalEvent(Person person) {
        return applicationEvents.stream(EmailChangeOwnerApprovalRequestedEvent.class)
                .filter(event -> event.personId().equals(person.getId()))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private String approvedVerificationTokenOf(Person target) {
        entityManager.flush();
        EmailVerificationToken token = emailVerificationTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(target.getId()))
                .findFirst().orElseThrow();
        ownerEmailChangeApprover.approve(token);
        String clearToken = "approved-" + token.getId();
        token.setTokenHash(TokenHashUtil.sha256Hex(clearToken));
        entityManager.flush();
        return clearToken;
    }

    private String tokenOf(EmailChangeRequestedEvent event) {
        return event.verificationLink().substring(event.verificationLink().indexOf("token=") + 6);
    }

    private String cancelTokenOf(EmailChangeRequestedEvent event) {
        return event.cancellationLink().substring(event.cancellationLink().indexOf("token=") + 6);
    }

    private List<ContactChangeLog> logOf(Person person) {
        entityManager.flush();
        return entityManager.createQuery("select l from ContactChangeLog l where l.personId = :id order by l.id",
                ContactChangeLog.class).setParameter("id", person.getId()).getResultList();
    }

    private RefreshToken persistRefreshToken(Person person, String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.setPerson(person);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setCreatedAt(LocalDateTime.now());
        entityManager.persist(token);
        entityManager.flush();
        return token;
    }

    private boolean isRevoked(RefreshToken token) {
        return entityManager.find(RefreshToken.class, token.getId()).getRevoked();
    }

    private void withClockOffset(Duration offset, ThrowingRunnable action) throws Exception {
        ApplicationTime.useClock(Clock.offset(Clock.systemDefaultZone(), offset));
        try {
            action.run();
        } finally {
            ApplicationTime.useClock(Clock.systemDefaultZone());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
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

    private ResultActions cancelEmailChange(String token) throws Exception {
        return mockMvc.perform(post("/cancel-email-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CancelEmailChangeRequest(token))));
    }

    private String unknownToken() {
        return "x".repeat(43);
    }

    @Test
    void cancelFromPreviousEmailDiscardsRequest() throws Exception {
        Person person = persistPerson("access.emailchg9", "70000210", "ALUMNO");
        updateOwnProfile(person, "nuevo9@example.com", null, PASSWORD).andExpect(status().isOk());
        EmailChangeRequestedEvent event = lastEmailChangeEvent(person);

        cancelEmailChange(cancelTokenOf(event)).andExpect(status().isOk());

        mockMvc.perform(get("/verify-email").param("token", tokenOf(event)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
        assertThat(reload(person).emailValue()).isEqualTo("access.emailchg9@example.com");
        cancelEmailChange(cancelTokenOf(event)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
        cancelEmailChange(tokenOf(event)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
    }

    @Test
    void cancelWithExpiredTokenIsRejectedAndKeepsTheRequestPending() throws Exception {
        Person person = persistPerson("access.emailchg40", "70000260", "ALUMNO");
        updateOwnProfile(person, "nuevo40@example.com", null, PASSWORD).andExpect(status().isOk());
        EmailChangeRequestedEvent event = lastEmailChangeEvent(person);

        ApplicationTime.useClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(49)));
        try {
            cancelEmailChange(cancelTokenOf(event)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
        } finally {
            ApplicationTime.useClock(Clock.systemDefaultZone());
        }
        mockMvc.perform(get("/verify-email").param("token", tokenOf(event))).andExpect(status().isOk());
        assertThat(reload(person).emailValue()).isEqualTo("nuevo40@example.com");
    }

    @Test
    void cancelWithUnknownTokenIsRejected() throws Exception {
        cancelEmailChange(unknownToken()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
    }

    @Test
    void cancelAfterConfirmIsRejectedAndDoesNotRevertTheNewEmail() throws Exception {
        Person person = persistPerson("access.emailchg41", "70000261", "ALUMNO");
        updateOwnProfile(person, "nuevo41@example.com", null, PASSWORD).andExpect(status().isOk());
        EmailChangeRequestedEvent event = lastEmailChangeEvent(person);
        mockMvc.perform(get("/verify-email").param("token", tokenOf(event))).andExpect(status().isOk());

        cancelEmailChange(cancelTokenOf(event)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));

        assertThat(reload(person).emailValue()).isEqualTo("nuevo41@example.com");
    }

    @Test
    void confirmAfterCancelIsRejectedAndKeepsThePreviousEmail() throws Exception {
        Person person = persistPerson("access.emailchg42", "70000262", "ALUMNO");
        updateOwnProfile(person, "nuevo42@example.com", null, PASSWORD).andExpect(status().isOk());
        EmailChangeRequestedEvent event = lastEmailChangeEvent(person);
        cancelEmailChange(cancelTokenOf(event)).andExpect(status().isOk());

        mockMvc.perform(get("/verify-email").param("token", tokenOf(event))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));

        assertThat(reload(person).emailValue()).isEqualTo("access.emailchg42@example.com");
    }

    @Test
    void cancelTokenTravelsOnlyInTheBody() throws Exception {
        Person person = persistPerson("access.emailchg43", "70000263", "ALUMNO");
        updateOwnProfile(person, "nuevo43@example.com", null, PASSWORD).andExpect(status().isOk());
        EmailChangeRequestedEvent event = lastEmailChangeEvent(person);

        mockMvc.perform(post("/cancel-email-change").param("token", cancelTokenOf(event)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));

        mockMvc.perform(get("/verify-email").param("token", tokenOf(event))).andExpect(status().isOk());
    }

    @Test
    void cancelWithInvalidTokenShapeIsRejectedByValidation() throws Exception {
        for (String token : new String[] {" ", "", "x".repeat(31), "x".repeat(129)}) {
            cancelEmailChange(token).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mockMvc.perform(post("/cancel-email-change").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void cancelWithoutBodyIsRejectedAsMalformed() throws Exception {
        mockMvc.perform(post("/cancel-email-change").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    void cancelAcceptsTheShortestAndLongestAllowedToken() throws Exception {
        cancelEmailChange("x".repeat(32)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
        cancelEmailChange("x".repeat(128)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));
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

        EmailChangeOwnerApprovalRequestedEvent event = lastOwnerApprovalEvent(teacher);
        assertThat(event.currentEmail()).isEqualTo("access.emailchg8b@example.com");
        assertThat(event.requestedEmail()).isEqualTo("docente.nuevo@example.com");
        List<ContactChangeLog> log = logOf(teacher);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getActorPersonId()).isEqualTo(admin.getId());
        resetStaffPassword(admin, teacher).andExpect(status().isConflict());
        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(issued -> issued.personId().equals(teacher.getId())).count()).isZero();
    }

    @Test
    void resetBlockedFor72HoursAfterConfirmation() throws Exception {
        Person admin = persistPerson("access.emailchg9a", "70000210", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg9b", "70000211", "DOCENTE");
        updateStaffEmail(admin, teacher, "docente9.nuevo@example.com").andExpect(status().isOk());
        mockMvc.perform(get("/verify-email").param("token", approvedVerificationTokenOf(teacher)))
                .andExpect(status().isOk());

        resetStaffPassword(admin, teacher).andExpect(status().isConflict());

        ApplicationTime.useClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(71)));
        try {
            resetStaffPassword(admin, teacher).andExpect(status().isConflict());
        } finally {
            ApplicationTime.useClock(Clock.systemDefaultZone());
        }
        ApplicationTime.useClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(73)));
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
    void staffEmailChangeDoesNotApplyUntilOwnerConfirms() throws Exception {
        Person admin = persistPerson("access.emailchg12a", "70000216", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg12b", "70000217", "DOCENTE");

        updateStaffEmail(admin, teacher, "docente12.nuevo@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("access.emailchg12b@example.com"));

        assertThat(reload(teacher).emailValue()).isEqualTo("access.emailchg12b@example.com");
    }

    @Test
    void ownerConfirmationActivatesTheNewEmail() throws Exception {
        Person admin = persistPerson("access.emailchg13a", "70000218", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg13b", "70000219", "DOCENTE");
        updateStaffEmail(admin, teacher, "docente13.nuevo@example.com").andExpect(status().isOk());

        mockMvc.perform(get("/verify-email").param("token", approvedVerificationTokenOf(teacher)))
                .andExpect(status().isOk());

        Person confirmed = reload(teacher);
        assertThat(confirmed.emailValue()).isEqualTo("docente13.nuevo@example.com");
        assertThat(confirmed.getEmailVerified()).isTrue();
    }

    @Test
    void sessionsRevokedOnConfirm() throws Exception {
        Person admin = persistPerson("access.emailchg17a", "70000226", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg17b", "70000227", "DOCENTE");
        Person bystander = persistPerson("access.emailchg17c", "70000228", "DOCENTE");
        RefreshToken teacherToken = persistRefreshToken(teacher, "hash-17-teacher");
        RefreshToken bystanderToken = persistRefreshToken(bystander, "hash-17-bystander");
        int versionBefore = reload(teacher).getTokenVersion();
        updateStaffEmail(admin, teacher, "docente17.nuevo@example.com").andExpect(status().isOk());
        assertThat(isRevoked(teacherToken)).isFalse();

        mockMvc.perform(get("/verify-email").param("token", approvedVerificationTokenOf(teacher)))
                .andExpect(status().isOk());

        assertThat(reload(teacher).getTokenVersion()).isGreaterThan(versionBefore);
        assertThat(isRevoked(teacherToken)).isTrue();
        assertThat(isRevoked(bystanderToken)).isFalse();
    }

    @Test
    void ownChangeConfirmationAlsoRevokesSessions() throws Exception {
        Person person = persistPerson("access.emailchg18", "70000229", "ALUMNO");
        RefreshToken token = persistRefreshToken(person, "hash-18-own");
        int versionBefore = reload(person).getTokenVersion();
        updateOwnProfile(person, "nuevo18@example.com", null, PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/verify-email").param("token", tokenOf(lastEmailChangeEvent(person))))
                .andExpect(status().isOk());

        assertThat(reload(person).getTokenVersion()).isGreaterThan(versionBefore);
        assertThat(isRevoked(token)).isTrue();
    }

    @Test
    void secondPendingChangeReplacesTheFirst() throws Exception {
        Person admin = persistPerson("access.emailchg19a", "70000230", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg19b", "70000231", "DOCENTE");
        updateStaffEmail(admin, teacher, "docente19.primero@example.com").andExpect(status().isOk());
        String firstToken = approvedVerificationTokenOf(teacher);
        updateStaffEmail(admin, teacher, "docente19.segundo@example.com").andExpect(status().isOk());
        String secondToken = approvedVerificationTokenOf(teacher);

        mockMvc.perform(get("/verify-email").param("token", firstToken)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/verify-email").param("token", secondToken)).andExpect(status().isOk());

        assertThat(reload(teacher).emailValue()).isEqualTo("docente19.segundo@example.com");
    }

    @Test
    void administrativeChangeCannotOverwriteAPendingChangeStartedByTheOwner() throws Exception {
        Person admin = persistPerson("access.emailchg20a", "70000232", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg20b", "70000233", "DOCENTE");
        updateOwnProfile(teacher, "docente20.propio@example.com", null, PASSWORD).andExpect(status().isOk());
        String ownerToken = tokenOf(lastEmailChangeEvent(teacher));

        updateStaffEmail(admin, teacher, "docente20.admin@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_PENDING_BY_OWNER"));

        mockMvc.perform(get("/verify-email").param("token", ownerToken)).andExpect(status().isOk());
        assertThat(reload(teacher).emailValue()).isEqualTo("docente20.propio@example.com");
    }

    @Test
    void administrativeChangeIsAllowedOnceTheOwnersPendingChangeExpired() throws Exception {
        Person admin = persistPerson("access.emailchg21a", "70000234", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg21b", "70000235", "DOCENTE");
        updateOwnProfile(teacher, "docente21.propio@example.com", null, PASSWORD).andExpect(status().isOk());

        withClockOffset(Duration.ofHours(49), () ->
                updateStaffEmail(admin, teacher, "docente21.admin@example.com").andExpect(status().isOk()));
    }

    @Test
    void unconfirmedExpiredChangeKeepsBlockingTheStaffReset() throws Exception {
        Person admin = persistPerson("access.emailchg22a", "70000236", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg22b", "70000237", "DOCENTE");
        updateStaffEmail(admin, teacher, "docente22.nuevo@example.com").andExpect(status().isOk());

        withClockOffset(Duration.ofHours(49), () -> resetStaffPassword(admin, teacher).andExpect(status().isConflict()));
    }

    @Test
    void previousAddressGetsNotice() throws Exception {
        Person admin = persistPerson("access.emailchg14a", "70000220", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg14b", "70000221", "DOCENTE");

        updateStaffEmail(admin, teacher, "docente14.nuevo@example.com").andExpect(status().isOk());

        EmailChangeOwnerApprovalRequestedEvent event = lastOwnerApprovalEvent(teacher);
        assertThat(event.currentEmail()).isEqualTo("access.emailchg14b@example.com");
        assertThat(event.requestedEmail()).isEqualTo("docente14.nuevo@example.com");
    }

    @Test
    void expiredTokenRejected() throws Exception {
        Person admin = persistPerson("access.emailchg15a", "70000222", "ADMINISTRATIVO");
        Person teacher = persistPerson("access.emailchg15b", "70000223", "DOCENTE");
        updateStaffEmail(admin, teacher, "docente15.nuevo@example.com").andExpect(status().isOk());
        String token = approvedVerificationTokenOf(teacher);

        ApplicationTime.useClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(49)));
        try {
            mockMvc.perform(get("/verify-email").param("token", token)).andExpect(status().isBadRequest());
        } finally {
            ApplicationTime.useClock(Clock.systemDefaultZone());
        }
        assertThat(reload(teacher).emailValue()).isEqualTo("access.emailchg15b@example.com");
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

package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.PasswordResetCodeRequester;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.personWithRoles;
import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.policyActingAs;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResetStaffPasswordServiceTest {

    private static final Long TARGET_ID = 70L;

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PasswordResetCodeRequester passwordResetCodeRequester = mock(PasswordResetCodeRequester.class);

    private ResetStaffPasswordService serviceActingAs(String actorRole) {
        return new ResetStaffPasswordService(personRepository, policyActingAs(actorRole), passwordResetCodeRequester);
    }

    private Person target(String... roles) {
        Person person = personWithRoles(TARGET_ID, StatusType.ACTIVE, roles);
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.of(person));
        return person;
    }

    @Test
    void directorResetsTheAdministrativePassword() {
        Person person = target("ADMINISTRATIVO");

        serviceActingAs("DIRECTOR").resetPassword(TARGET_ID);

        verify(passwordResetCodeRequester).requestPasswordResetCode(person.emailValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCENTE", "PRECEPTOR"})
    void administrativeResetsATeacherOrPreceptorPassword(String role) {
        Person person = target(role);

        serviceActingAs("ADMINISTRATIVO").resetPassword(TARGET_ID);

        verify(passwordResetCodeRequester).requestPasswordResetCode(person.emailValue());
    }

    @Test
    void administrativeCannotResetAnotherAdministrativePassword() {
        target("ADMINISTRATIVO");

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").resetPassword(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        verifyNoInteractions(passwordResetCodeRequester);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCENTE", "PRECEPTOR", "ALUMNO"})
    void actorsWithoutStaffManagementRoleCannotResetAnyStaffPassword(String actorRole) {
        target("DOCENTE");

        assertThatThrownBy(() -> serviceActingAs(actorRole).resetPassword(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        verifyNoInteractions(passwordResetCodeRequester);
    }

    @Test
    void directorCannotResetATeacherPasswordBecauseThePolicyOnlyLetsThemManageAdministratives() {
        target("DOCENTE");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").resetPassword(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        verifyNoInteractions(passwordResetCodeRequester);
    }

    @Test
    void resetUsesTheStaffMemberEmailEvenWhenTheyAlsoHaveNonStaffRoles() {
        Person person = target("ALUMNO", "DOCENTE");

        serviceActingAs("ADMINISTRATIVO").resetPassword(TARGET_ID);

        verify(passwordResetCodeRequester).requestPasswordResetCode(person.emailValue());
    }

    @Test
    void resetOfAnUnknownPersonFailsWithNotFoundAndSendsNothing() {
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").resetPassword(TARGET_ID)).isInstanceOf(PersonNotFoundException.class);
        verifyNoInteractions(passwordResetCodeRequester);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALUMNO", "DIRECTOR"})
    void resetOfAPersonWithoutStaffRoleFailsWithNotFoundAndSendsNothing(String role) {
        target(role);

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").resetPassword(TARGET_ID)).isInstanceOf(PersonNotFoundException.class);
        verify(passwordResetCodeRequester, never()).requestPasswordResetCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resetOfAPersonWithNoRolesFailsWithNotFound() {
        target();

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").resetPassword(TARGET_ID)).isInstanceOf(PersonNotFoundException.class);
    }
}

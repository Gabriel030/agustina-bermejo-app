package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.personWithRoles;
import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.policyActingAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawStaffServiceTest {

    private static final Long TARGET_ID = 60L;

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase = mock(RevokeAllSessionsUseCase.class);

    private WithdrawStaffService serviceActingAs(String actorRole) {
        return new WithdrawStaffService(personRepository, policyActingAs(actorRole), revokeAllSessionsUseCase);
    }

    private Person target(StatusType status, String... roles) {
        Person person = personWithRoles(TARGET_ID, status, roles);
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.of(person));
        return person;
    }

    @Test
    void administrativeWithdrawsATeacherAndRevokesTheirSessions() {
        Person teacher = target(StatusType.ACTIVE, "DOCENTE");

        serviceActingAs("ADMINISTRATIVO").withdraw(TARGET_ID);

        assertThat(teacher.getStatus()).isEqualTo(StatusType.INACTIVE);
        InOrder order = inOrder(personRepository, revokeAllSessionsUseCase);
        order.verify(personRepository).save(teacher);
        order.verify(revokeAllSessionsUseCase).revokeAll(teacher);
    }

    @Test
    void administrativeWithdrawsAPreceptor() {
        Person preceptor = target(StatusType.ACTIVE, "PRECEPTOR");

        serviceActingAs("ADMINISTRATIVO").withdraw(TARGET_ID);

        assertThat(preceptor.getStatus()).isEqualTo(StatusType.INACTIVE);
    }

    @Test
    void directorWithdrawsAnAdministrativeAndRevokesTheirSessions() {
        Person administrative = target(StatusType.ACTIVE, "ADMINISTRATIVO");

        serviceActingAs("DIRECTOR").withdraw(TARGET_ID);

        assertThat(administrative.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(revokeAllSessionsUseCase).revokeAll(administrative);
    }

    @Test
    void withdrawingAnUnknownPersonFailsWithNotFound() {
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").withdraw(TARGET_ID))
                .isInstanceOf(PersonNotFoundException.class);
        verify(personRepository, never()).save(any());
        verify(revokeAllSessionsUseCase, never()).revokeAll(any());
    }

    @Test
    void withdrawingAPersonWithoutStaffRoleFailsWithNotFoundAndKeepsThemActive() {
        Person student = target(StatusType.ACTIVE, "ALUMNO");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").withdraw(TARGET_ID))
                .isInstanceOf(PersonNotFoundException.class);
        assertThat(student.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(revokeAllSessionsUseCase, never()).revokeAll(any());
    }

    @Test
    void administrativeCannotWithdrawAnotherAdministrative() {
        Person administrative = target(StatusType.ACTIVE, "ADMINISTRATIVO");

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").withdraw(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        assertThat(administrative.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(personRepository, never()).save(any());
        verify(revokeAllSessionsUseCase, never()).revokeAll(any());
    }

    @Test
    void directorCannotWithdrawATeacher() {
        target(StatusType.ACTIVE, "DOCENTE");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").withdraw(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        verify(revokeAllSessionsUseCase, never()).revokeAll(any());
    }

    @Test
    void aTeacherActorCannotWithdrawAnyone() {
        target(StatusType.ACTIVE, "PRECEPTOR");

        assertThatThrownBy(() -> serviceActingAs("DOCENTE").withdraw(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void withdrawingAnAlreadyInactiveStaffMemberKeepsThemInactiveAndRevokesSessionsAgain() {
        Person teacher = target(StatusType.INACTIVE, "DOCENTE");

        serviceActingAs("ADMINISTRATIVO").withdraw(TARGET_ID);

        assertThat(teacher.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(revokeAllSessionsUseCase).revokeAll(teacher);
    }
}

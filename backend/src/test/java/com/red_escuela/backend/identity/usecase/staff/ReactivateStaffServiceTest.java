package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.personWithRoles;
import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.policyActingAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactivateStaffServiceTest {

    private static final Long TARGET_ID = 50L;

    private final PersonRepository personRepository = mock(PersonRepository.class);

    private ReactivateStaffService serviceActingAs(String actorRole) {
        return new ReactivateStaffService(personRepository, policyActingAs(actorRole));
    }

    private Person target(StatusType status, String... roles) {
        Person person = personWithRoles(TARGET_ID, status, roles);
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.of(person));
        return person;
    }

    @Test
    void administrativeReactivatesAWithdrawnTeacher() {
        Person teacher = target(StatusType.INACTIVE, "DOCENTE");

        serviceActingAs("ADMINISTRATIVO").reactivate(TARGET_ID);

        assertThat(teacher.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(personRepository).save(teacher);
    }

    @Test
    void directorReactivatesAWithdrawnAdministrative() {
        Person administrative = target(StatusType.INACTIVE, "ADMINISTRATIVO");

        serviceActingAs("DIRECTOR").reactivate(TARGET_ID);

        assertThat(administrative.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void reactivatingAnUnknownPersonFailsWithNotFound() {
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").reactivate(TARGET_ID))
                .isInstanceOf(PersonNotFoundException.class);
        verify(personRepository, never()).save(any());
    }

    @Test
    void reactivatingAPersonWithoutStaffRoleFailsWithNotFound() {
        Person student = target(StatusType.INACTIVE, "ALUMNO");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").reactivate(TARGET_ID))
                .isInstanceOf(PersonNotFoundException.class);
        assertThat(student.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(personRepository, never()).save(any());
    }

    @Test
    void administrativeCannotReactivateAnotherAdministrative() {
        Person administrative = target(StatusType.INACTIVE, "ADMINISTRATIVO");

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").reactivate(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        assertThat(administrative.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(personRepository, never()).save(any());
    }

    @Test
    void directorCannotReactivateATeacher() {
        target(StatusType.INACTIVE, "DOCENTE");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").reactivate(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        verify(personRepository, never()).save(any());
    }

    @Test
    void aTeacherActorCannotReactivateAnyone() {
        target(StatusType.INACTIVE, "PRECEPTOR");

        assertThatThrownBy(() -> serviceActingAs("DOCENTE").reactivate(TARGET_ID))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void reactivatingAnAlreadyActiveStaffMemberFailsWithConflict() {
        Person teacher = target(StatusType.ACTIVE, "DOCENTE");

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").reactivate(TARGET_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(teacher.getStatus()).isEqualTo(StatusType.ACTIVE);
        verify(personRepository, never()).save(any());
    }

    @Test
    void reactivatingADeletedStaffMemberFailsWithConflict() {
        target(StatusType.DELETED, "DOCENTE");

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").reactivate(TARGET_ID))
                .isInstanceOf(ResponseStatusException.class);
        verify(personRepository, never()).save(any());
    }
}

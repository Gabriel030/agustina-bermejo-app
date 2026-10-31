package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;

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

class UpdateStaffServiceTest {

    private static final Long TARGET_ID = 80L;

    private final PersonRepository personRepository = mock(PersonRepository.class);

    private UpdateStaffService serviceActingAs(String actorRole) {
        return new UpdateStaffService(personRepository, policyActingAs(actorRole));
    }

    private Person target(String... roles) {
        Person person = personWithRoles(TARGET_ID, StatusType.ACTIVE, roles);
        person.setPhoneNumber("111");
        Address address = person.getAddress();
        address.setStreet("Calle");
        address.setNumber("1");
        address.setFloor("2");
        address.setCity("Ciudad");
        address.setState("Provincia");
        address.setPostalCode("1000");
        address.setCountry("AR");
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.of(person));
        when(personRepository.save(person)).thenReturn(person);
        return person;
    }

    private UpdateStaffRequest onlyNulls() {
        return new UpdateStaffRequest(null, null, null, null, null, null, null, null, null);
    }

    @Test
    void administrativeUpdatesEveryFieldOfATeacher() {
        Person teacher = target("DOCENTE");
        var request = new UpdateStaffRequest("new@example.com", "222", "Otra", "9", "3", "Villa", "Cordoba", "5000", "UY");

        StaffUseCaseResult result = serviceActingAs("ADMINISTRATIVO").update(new UpdateStaffCommand(TARGET_ID, request));

        assertThat(teacher.emailValue()).isEqualTo("new@example.com");
        assertThat(teacher.getPhoneNumber()).isEqualTo("222");
        Address address = teacher.getAddress();
        assertThat(address.getStreet()).isEqualTo("Otra");
        assertThat(address.getNumber()).isEqualTo("9");
        assertThat(address.getFloor()).isEqualTo("3");
        assertThat(address.getCity()).isEqualTo("Villa");
        assertThat(address.getState()).isEqualTo("Cordoba");
        assertThat(address.getPostalCode()).isEqualTo("5000");
        assertThat(address.getCountry()).isEqualTo("UY");
        assertThat(result.person()).isSameAs(teacher);
        assertThat(result.role()).isEqualTo("DOCENTE");
        verify(personRepository).save(teacher);
    }

    @Test
    void nullFieldsLeaveTheCurrentValuesUntouched() {
        Person teacher = target("PRECEPTOR");
        String emailBefore = teacher.emailValue();

        serviceActingAs("ADMINISTRATIVO").update(new UpdateStaffCommand(TARGET_ID, onlyNulls()));

        assertThat(teacher.emailValue()).isEqualTo(emailBefore);
        assertThat(teacher.getPhoneNumber()).isEqualTo("111");
        Address address = teacher.getAddress();
        assertThat(address.getStreet()).isEqualTo("Calle");
        assertThat(address.getNumber()).isEqualTo("1");
        assertThat(address.getFloor()).isEqualTo("2");
        assertThat(address.getCity()).isEqualTo("Ciudad");
        assertThat(address.getState()).isEqualTo("Provincia");
        assertThat(address.getPostalCode()).isEqualTo("1000");
        assertThat(address.getCountry()).isEqualTo("AR");
    }

    @Test
    void eachFieldIsUpdatedIndependentlyOfTheOthers() {
        Person teacher = target("DOCENTE");

        serviceActingAs("ADMINISTRATIVO").update(new UpdateStaffCommand(TARGET_ID,
                new UpdateStaffRequest(null, "333", null, null, "7", null, null, null, "CL")));

        assertThat(teacher.getPhoneNumber()).isEqualTo("333");
        assertThat(teacher.getAddress().getFloor()).isEqualTo("7");
        assertThat(teacher.getAddress().getCountry()).isEqualTo("CL");
        assertThat(teacher.getAddress().getStreet()).isEqualTo("Calle");
        assertThat(teacher.getAddress().getCity()).isEqualTo("Ciudad");
    }

    @Test
    void directorUpdatesAnAdministrative() {
        Person administrative = target("ADMINISTRATIVO");

        StaffUseCaseResult result = serviceActingAs("DIRECTOR").update(new UpdateStaffCommand(TARGET_ID,
                new UpdateStaffRequest(null, "444", null, null, null, null, null, null, null)));

        assertThat(administrative.getPhoneNumber()).isEqualTo("444");
        assertThat(result.role()).isEqualTo("ADMINISTRATIVO");
    }

    @Test
    void updatingAnUnknownPersonFailsWithNotFound() {
        when(personRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").update(new UpdateStaffCommand(TARGET_ID, onlyNulls())))
                .isInstanceOf(PersonNotFoundException.class);
        verify(personRepository, never()).save(any());
    }

    @Test
    void administrativeCannotUpdateAnotherAdministrative() {
        Person administrative = target("ADMINISTRATIVO");

        assertThatThrownBy(() -> serviceActingAs("ADMINISTRATIVO").update(new UpdateStaffCommand(TARGET_ID,
                new UpdateStaffRequest("x@example.com", null, null, null, null, null, null, null, null))))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        assertThat(administrative.emailValue()).isNotEqualTo("x@example.com");
        verify(personRepository, never()).save(any());
    }

    @Test
    void directorCannotUpdateATeacher() {
        target("DOCENTE");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").update(new UpdateStaffCommand(TARGET_ID, onlyNulls())))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void updatingAPersonWithoutStaffRoleIsForbidden() {
        target("ALUMNO");

        assertThatThrownBy(() -> serviceActingAs("DIRECTOR").update(new UpdateStaffCommand(TARGET_ID, onlyNulls())))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        verify(personRepository, never()).save(any());
    }
}

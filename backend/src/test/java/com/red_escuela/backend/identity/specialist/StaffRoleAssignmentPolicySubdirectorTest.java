package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffRoleAssignmentPolicySubdirectorTest {

    @Test
    void directorCanAssignSubdirector() {
        assertThatCode(() -> policyActingAs("DIRECTOR").requireCanAssign("SUBDIRECTOR")).doesNotThrowAnyException();
    }

    @Test
    void administrativeCannotAssignSubdirector() {
        assertThatThrownBy(() -> policyActingAs("ADMINISTRATIVO").requireCanAssign("SUBDIRECTOR"))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void subdirectorCannotAssignSubdirectorNorAdministrative() {
        assertThatThrownBy(() -> policyActingAs("SUBDIRECTOR").requireCanAssign("SUBDIRECTOR"))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        assertThatThrownBy(() -> policyActingAs("SUBDIRECTOR").requireCanAssign("ADMINISTRATIVO"))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void subdirectorKeepsAdministrativeAssignmentOfTeachingStaff() {
        assertThatCode(() -> policyActingAs("SUBDIRECTOR").requireCanAssign("DOCENTE")).doesNotThrowAnyException();
        assertThatCode(() -> policyActingAs("SUBDIRECTOR").requireCanAssign("PRECEPTOR")).doesNotThrowAnyException();
    }

    @Test
    void subdirectorIsStaffAndOnlyTheDirectorSeesIt() {
        assertThat(StaffRoleAssignmentPolicy.STAFF_ROLES).contains("SUBDIRECTOR");
        assertThat(policyActingAs("DIRECTOR").visibleRolesForCurrentActor()).contains("SUBDIRECTOR");
        assertThat(policyActingAs("ADMINISTRATIVO").visibleRolesForCurrentActor()).doesNotContain("SUBDIRECTOR");
    }

    private StaffRoleAssignmentPolicy policyActingAs(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Person actor = new Person();
        actor.getRoles().add(role);
        CurrentPersonService currentPersonService = mock(CurrentPersonService.class);
        when(currentPersonService.resolveCurrentPerson()).thenReturn(actor);
        return new StaffRoleAssignmentPolicy(currentPersonService, mock(EmailChangeRequester.class));
    }
}

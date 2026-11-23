package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.policy.StaffVisibilityPolicy;
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

    @Test
    void subdirectorCannotManageDirectorAnotherSubdirectorNorItself() {
        StaffRoleAssignmentPolicy policy = policyActingAs("SUBDIRECTOR");
        Person director = personWithRole("DIRECTOR");
        Person subdirector = personWithRole("SUBDIRECTOR");

        assertThat(policy.resolveStaffRole(director)).isNull();
        assertThatThrownBy(() -> policy.requireManageableOrNotFound(1L, policy.resolveStaffRole(director)))
                .isInstanceOf(PersonNotFoundException.class);
        assertThatThrownBy(() -> policy.requireCanAssign(policy.resolveStaffRole(director)))
                .isInstanceOf(RoleNotAssignableByActorException.class);
        assertThat(policy.resolveStaffRole(subdirector)).isEqualTo("SUBDIRECTOR");
        assertThatThrownBy(() -> policy.requireManageableOrNotFound(2L, policy.resolveStaffRole(subdirector)))
                .isInstanceOf(PersonNotFoundException.class);
        assertThatThrownBy(() -> policy.requireCanAssign(policy.resolveStaffRole(subdirector)))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void subdirectorCanManageTeacherAndPreceptor() {
        StaffRoleAssignmentPolicy policy = policyActingAs("SUBDIRECTOR");

        assertThatCode(() -> policy.requireManageableOrNotFound(3L, policy.resolveStaffRole(personWithRole("DOCENTE"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireManageableOrNotFound(4L, policy.resolveStaffRole(personWithRole("PRECEPTOR"))))
                .doesNotThrowAnyException();
    }

    @Test
    void directorCanManageSubdirector() {
        StaffRoleAssignmentPolicy policy = policyActingAs("DIRECTOR");

        assertThatCode(() -> policy.requireManageableOrNotFound(5L, policy.resolveStaffRole(personWithRole("SUBDIRECTOR"))))
                .doesNotThrowAnyException();
    }

    @Test
    void subdirectorSeesOnlyTeacherAndPreceptor() {
        assertThat(policyActingAs("SUBDIRECTOR").visibleRolesForCurrentActor()).containsExactlyInAnyOrder("DOCENTE", "PRECEPTOR");
        assertThat(StaffVisibilityPolicy.visibleRoles(java.util.List.of("SUBDIRECTOR"))).containsExactlyInAnyOrder("DOCENTE", "PRECEPTOR");
        assertThat(StaffVisibilityPolicy.canView(java.util.List.of("SUBDIRECTOR"), java.util.List.of("SUBDIRECTOR"))).isFalse();
        assertThat(StaffVisibilityPolicy.canView(java.util.List.of("DIRECTOR"), java.util.List.of("SUBDIRECTOR"))).isTrue();
    }

    private Person personWithRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Person person = new Person();
        person.getRoles().add(role);
        return person;
    }
}

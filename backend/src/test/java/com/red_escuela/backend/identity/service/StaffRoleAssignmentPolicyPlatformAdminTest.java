package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-484")
class StaffRoleAssignmentPolicyPlatformAdminTest {

    @Test
    void directorCannotGrantPlatformAdmin() {
        assertThatThrownBy(() -> policyActingAs("DIRECTOR").requireCanAssign("PLATFORM_ADMIN"))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void administrativeCannotGrantPlatformAdmin() {
        assertThatThrownBy(() -> policyActingAs("ADMINISTRATIVO").requireCanAssign("PLATFORM_ADMIN"))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void platformAdminCannotGrantPlatformAdminEither() {
        assertThatThrownBy(() -> policyActingAs("PLATFORM_ADMIN").requireCanAssign("PLATFORM_ADMIN"))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void platformAdminIsNeitherStaffNorVisibleToDirector() {
        assertThat(StaffRoleAssignmentPolicy.STAFF_ROLES).doesNotContain("PLATFORM_ADMIN");
        assertThat(policyActingAs("DIRECTOR").visibleRolesForCurrentActor()).doesNotContain("PLATFORM_ADMIN");
    }

    private StaffRoleAssignmentPolicy policyActingAs(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Person actor = new Person();
        actor.getRoles().add(role);
        CurrentPersonService currentPersonService = mock(CurrentPersonService.class);
        when(currentPersonService.resolveCurrentPerson()).thenReturn(actor);
        return new StaffRoleAssignmentPolicy(currentPersonService);
    }
}

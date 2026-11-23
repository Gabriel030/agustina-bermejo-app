package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAuthorityExpanderTest {

    @Test
    void subdirectorExpandsToItselfAndAdministrative() {
        assertThat(RoleAuthorityExpander.expand(List.of("SUBDIRECTOR"))).isEqualTo(Set.of("SUBDIRECTOR", "ADMINISTRATIVO"));
    }

    @Test
    void rolesWithoutInheritanceAreUnchanged() {
        assertThat(RoleAuthorityExpander.expand(List.of("DOCENTE", "DIRECTOR", "ADMINISTRATIVO")))
                .isEqualTo(Set.of("DOCENTE", "DIRECTOR", "ADMINISTRATIVO"));
    }
}

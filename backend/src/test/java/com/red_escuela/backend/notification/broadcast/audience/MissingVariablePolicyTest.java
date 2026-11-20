package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.notification.broadcast.audience.domain.MissingVariablePolicy;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MissingVariablePolicyTest {

    private static boolean missing(Set<TemplateVariable> referenced, String first, String last, boolean institution) {
        return MissingVariablePolicy.isMissingAnyVariable(referenced, first, last, institution);
    }

    @Test
    void noReferencedVariablesIsNeverMissing() {
        assertThat(missing(EnumSet.noneOf(TemplateVariable.class), null, null, false)).isFalse();
    }

    @Test
    void blankOrNullFirstNameIsMissingOnlyWhenReferenced() {
        assertThat(missing(EnumSet.of(TemplateVariable.FIRST_NAME), " ", "Perez", true)).isTrue();
        assertThat(missing(EnumSet.of(TemplateVariable.FIRST_NAME), null, "Perez", true)).isTrue();
        assertThat(missing(EnumSet.of(TemplateVariable.LAST_NAME), "", "Perez", true)).isFalse();
    }

    @Test
    void blankLastNameIsMissingWhenReferenced() {
        assertThat(missing(EnumSet.of(TemplateVariable.LAST_NAME), "Ana", "", true)).isTrue();
    }

    @Test
    void unconfiguredInstitutionIsMissingWhenReferenced() {
        assertThat(missing(EnumSet.of(TemplateVariable.INSTITUTION), "Ana", "Perez", false)).isTrue();
        assertThat(missing(EnumSet.of(TemplateVariable.INSTITUTION), "Ana", "Perez", true)).isFalse();
    }

    @Test
    void variablesWithoutPersonSourceAreMissingForEveryone() {
        for (TemplateVariable variable : EnumSet.of(TemplateVariable.PROGRAM, TemplateVariable.SUBJECT, TemplateVariable.SECTION)) {
            assertThat(missing(EnumSet.of(variable), "Ana", "Perez", true)).isTrue();
        }
    }

    @Test
    void unsubscribeLinkIsNeverMissing() {
        assertThat(missing(EnumSet.of(TemplateVariable.UNSUBSCRIBE_LINK), null, null, false)).isFalse();
    }
}

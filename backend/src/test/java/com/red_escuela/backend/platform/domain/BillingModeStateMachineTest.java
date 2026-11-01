package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.exception.InvalidBillingModeTransitionException;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingModeStateMachineTest {

    private static final BillingModeChangeClearance CUTOVER_DONE = new BillingModeChangeClearance(true, false);
    private static final BillingModeChangeClearance PREFLIGHT_PASSED = new BillingModeChangeClearance(false, true);

    @Test
    void offToMirrorIsAllowed() {
        assertAllowed(BillingMode.OFF, BillingMode.MIRROR, BillingModeChangeClearance.NONE);
    }

    @Test
    void offToFullIsAllowedWithoutCutover() {
        assertAllowed(BillingMode.OFF, BillingMode.FULL, BillingModeChangeClearance.NONE);
    }

    @Test
    void mirrorToOffIsAllowed() {
        assertAllowed(BillingMode.MIRROR, BillingMode.OFF, BillingModeChangeClearance.NONE);
    }

    @Test
    void mirrorToFullWithoutCutoverIsRejected() {
        assertThatThrownBy(() -> BillingModeStateMachine.requireTransitionAllowed(BillingMode.MIRROR, BillingMode.FULL, PREFLIGHT_PASSED))
                .isInstanceOf(InvalidBillingModeTransitionException.class)
                .hasMessageContaining("corte");
    }

    @Test
    void mirrorToFullWithCutoverIsAllowed() {
        assertAllowed(BillingMode.MIRROR, BillingMode.FULL, CUTOVER_DONE);
    }

    @Test
    void fullToOffRequiresPreflight() {
        assertThatThrownBy(() -> BillingModeStateMachine.requireTransitionAllowed(BillingMode.FULL, BillingMode.OFF, CUTOVER_DONE))
                .isInstanceOf(InvalidBillingModeTransitionException.class)
                .hasMessageContaining("verificación previa");
        assertAllowed(BillingMode.FULL, BillingMode.OFF, PREFLIGHT_PASSED);
    }

    @Test
    void fullToMirrorRequiresPreflight() {
        assertThatThrownBy(() -> BillingModeStateMachine.requireTransitionAllowed(BillingMode.FULL, BillingMode.MIRROR, BillingModeChangeClearance.NONE))
                .isInstanceOf(InvalidBillingModeTransitionException.class);
        assertAllowed(BillingMode.FULL, BillingMode.MIRROR, PREFLIGHT_PASSED);
    }

    @Test
    void stayingInTheSameModeIsRejectedForEveryMode() {
        for (BillingMode mode : BillingMode.values()) {
            assertThatThrownBy(() -> BillingModeStateMachine.requireTransitionAllowed(mode, mode, new BillingModeChangeClearance(true, true)))
                    .isInstanceOf(InvalidBillingModeTransitionException.class)
                    .hasMessageContaining("no puede pasar de " + mode + " a " + mode);
        }
    }

    @Test
    void everyOffAndMirrorTransitionToADifferentModeNeedsNoClearanceExceptMirrorToFull() {
        long needingNothing = 0;
        for (BillingMode from : BillingMode.values()) {
            for (BillingMode to : BillingMode.values()) {
                if (from == to) {
                    continue;
                }
                try {
                    BillingModeStateMachine.requireTransitionAllowed(from, to, BillingModeChangeClearance.NONE);
                    needingNothing++;
                } catch (InvalidBillingModeTransitionException expected) {
                    assertThat(from == BillingMode.FULL || (from == BillingMode.MIRROR && to == BillingMode.FULL)).isTrue();
                }
            }
        }
        assertThat(needingNothing).isEqualTo(3);
    }

    private static void assertAllowed(BillingMode from, BillingMode to, BillingModeChangeClearance clearance) {
        assertThatCode(() -> BillingModeStateMachine.requireTransitionAllowed(from, to, clearance)).doesNotThrowAnyException();
    }
}

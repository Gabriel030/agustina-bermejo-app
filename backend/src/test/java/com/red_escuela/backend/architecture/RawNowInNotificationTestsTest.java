package com.red_escuela.backend.architecture;

import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class RawNowInNotificationTestsTest {

    private static final String NOTIFICATION_PACKAGE = "com.red_escuela.backend.notification";
    private static final String ARCHITECTURE_PACKAGE = "com.red_escuela.backend.architecture";
    private static final Set<String> TIME_TYPES = Set.of(
            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime", "java.time.YearMonth",
            "java.time.Instant", "java.time.ZonedDateTime", "java.time.OffsetDateTime");

    @Test
    void notificationTestsReadTimeOnlyThroughAnInjectedClock() {
        assertThat(rawNowCalls(ImportedApplicationClasses.allTests(), NOTIFICATION_PACKAGE)).isEmpty();
    }

    @Test
    void ruleDetectsRawNowInGuardedPackage() {
        assertThat(rawNowCalls(ImportedApplicationClasses.allTests(), ARCHITECTURE_PACKAGE)).anyMatch(violation -> violation.contains("Offender"));
    }

    static final class Offender {
        Object readRawNow() {
            return java.time.Instant.now();
        }
    }

    private static Set<String> rawNowCalls(JavaClasses classes, String guardedPackage) {
        Set<String> violations = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            String packageName = javaClass.getPackageName();
            if (!packageName.equals(guardedPackage) && !packageName.startsWith(guardedPackage + ".")) {
                continue;
            }
            for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                if (TIME_TYPES.contains(call.getTargetOwner().getName()) && call.getName().equals("now")
                        && call.getTarget().getRawParameterTypes().isEmpty()) {
                    violations.add(javaClass.getName() + " -> " + call.getTargetOwner().getSimpleName() + ".now() (" + call.getSourceCodeLocation() + ")");
                }
            }
        }
        return violations;
    }
}

package com.red_escuela.backend.shared.time;

import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

public final class RawNowRule {

    private static final String ROOT = "com.red_escuela.backend";
    private static final Set<String> TIME_TYPES = Set.of(
            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime", "java.time.YearMonth",
            "java.time.Instant", "java.time.ZonedDateTime", "java.time.OffsetDateTime");
    private static final Set<String> GUARDED_PACKAGES = Set.of(
            ROOT + ".billing", ROOT + ".enrollment", ROOT + ".graduation");
    private static final Set<String> GUARDED_CLASSES = Set.of(
            ROOT + ".evaluation.service.ExamBoardRegistrationService",
            ROOT + ".evaluation.service.ExamBoardService");
    private static final Set<String> NEVER_GUARDED = Set.of(
            ROOT + ".shared.time.ApplicationTime", ROOT + ".config.TimeZoneConfig");

    private RawNowRule() {
    }

    public static JavaClasses importMainClasses() {
        return ImportedApplicationClasses.main();
    }

    public static Set<String> guardedViolations(JavaClasses classes) {
        Set<String> violations = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (isGuarded(javaClass)) {
                collect(javaClass, violations);
            }
        }
        return violations;
    }

    public static Set<String> offendingClassesOutsideGuard(JavaClasses classes) {
        Set<String> offenders = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (!isGuarded(javaClass) && !NEVER_GUARDED.contains(topLevelName(javaClass))) {
                Set<String> found = new TreeSet<>();
                collect(javaClass, found);
                if (!found.isEmpty()) {
                    offenders.add(topLevelName(javaClass));
                }
            }
        }
        return offenders;
    }

    public static Set<String> frozenBaseline() {
        try (InputStream in = RawNowRule.class.getResourceAsStream("/raw-now-baseline.txt")) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> baseline = new TreeSet<>();
            for (String line : content.split("\\R")) {
                if (!line.isBlank()) {
                    baseline.add(line.trim());
                }
            }
            return baseline;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isGuarded(JavaClass javaClass) {
        String top = topLevelName(javaClass);
        if (GUARDED_CLASSES.contains(top)) {
            return true;
        }
        String packageName = javaClass.getPackageName();
        return GUARDED_PACKAGES.stream().anyMatch(guarded -> packageName.equals(guarded) || packageName.startsWith(guarded + "."));
    }

    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int inner = name.indexOf('$');
        return inner < 0 ? name : name.substring(0, inner);
    }

    private static void collect(JavaClass javaClass, Set<String> violations) {
        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
            String owner = call.getTargetOwner().getName();
            boolean rawNow = TIME_TYPES.contains(owner)
                    && call.getName().equals("now")
                    && call.getTarget().getRawParameterTypes().isEmpty();
            boolean systemDefaultClock = owner.equals("java.time.Clock") && call.getName().equals("systemDefaultZone");
            if (rawNow || systemDefaultClock) {
                violations.add(javaClass.getName() + " -> " + owner.substring(owner.lastIndexOf('.') + 1) + "." + call.getName()
                        + "() (" + call.getSourceCodeLocation() + ")");
            }
        }
    }
}

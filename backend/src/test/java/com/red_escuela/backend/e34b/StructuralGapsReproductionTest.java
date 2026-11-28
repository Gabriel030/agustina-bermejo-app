package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralGapsReproductionTest {

    private static final String ROOT = "com.red_escuela.backend";
    private static final int MAX_DEPTH = 5;

    private final JavaClasses main = ImportedApplicationClasses.main();

    private boolean callsMethod(JavaClass caller, Class<?> owner, String methodName) {
        return caller.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getName().equals(owner.getName()) && call.getName().equals(methodName));
    }

    private boolean isScheduledJob(JavaClass javaClass) {
        return javaClass.getMethods().stream()
                .anyMatch(method -> method.isAnnotatedWith(Scheduled.class.getName()));
    }

    private boolean someScheduledJobReachesCallTo(Class<?> owner, String... methodNames) {
        Set<String> visited = new HashSet<>();
        Deque<JavaClass> pending = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        main.stream().filter(this::isScheduledJob).forEach(job -> {
            pending.add(job);
            depths.add(0);
        });
        while (!pending.isEmpty()) {
            JavaClass current = pending.poll();
            int depth = depths.poll();
            if (!visited.add(current.getName())) {
                continue;
            }
            if (Arrays.stream(methodNames).anyMatch(name -> callsMethod(current, owner, name))) {
                return true;
            }
            if (depth >= MAX_DEPTH) {
                continue;
            }
            current.getDirectDependenciesFromSelf().stream()
                    .map(dependency -> dependency.getTargetClass())
                    .filter(target -> target.getName().startsWith(ROOT))
                    .forEach(target -> {
                        pending.add(target);
                        depths.add(depth + 1);
                    });
        }
        return false;
    }

    @Test
    @Disabled("E34-B-P3")
    void someScheduledJobClosesTermsAfterTheirEndDate() {
        assertThat(someScheduledJobReachesCallTo(Term.class, "setStatus")).isTrue();
    }

    @Test
    @Disabled("E34-B-P3")
    void someScheduledJobClosesOrNotifiesExamBoardsWithPastExamDate() {
        assertThat(someScheduledJobReachesCallTo(ExamBoard.class, "setStatus", "close")).isTrue();
    }

    @Test
    @Disabled("E34-B-P7")
    void someScheduledJobClosesSectionsCyclesAndProgramEnrollmentsAfterTermEnd() {
        assertThat(someScheduledJobReachesCallTo(AcademicOfferingCycle.class, "setStatus")).as("cycle").isTrue();
        assertThat(someScheduledJobReachesCallTo(SubjectSection.class, "setStatus")).as("section").isTrue();
        assertThat(someScheduledJobReachesCallTo(ProgramEnrollment.class, "setStatus")).as("program enrollment").isTrue();
    }

    @Test
    void scheduledAnnotationIsDetectedByTheInventory() {
        JavaClass job = main.get("com.red_escuela.backend.enrollment.service.WaitlistExpiryJob");

        assertThat(isScheduledJob(job)).isTrue();
        assertThat(job.getMethods().stream().flatMap(method -> method.getAnnotations().stream())
                .map(JavaAnnotation::getRawType).map(JavaClass::getName)).contains(Scheduled.class.getName());
    }
}

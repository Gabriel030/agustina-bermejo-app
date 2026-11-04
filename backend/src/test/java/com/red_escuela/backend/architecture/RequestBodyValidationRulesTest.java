package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class RequestBodyValidationRulesTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();

    private static final Set<String> JUSTIFIED_EXCEPTIONS = Set.of();

    private static boolean hasAnnotation(JavaParameter parameter, String annotationName) {
        return parameter.getAnnotations().stream().map(JavaAnnotation::getRawType).anyMatch(type -> type.getName().equals(annotationName));
    }

    private static ArchCondition<JavaMethod> validateEveryWriteBody() {
        return new ArchCondition<>("validate every @RequestBody with @Valid or a self validating request") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String signature = method.getOwner().getSimpleName() + "." + method.getName();
                if (JUSTIFIED_EXCEPTIONS.contains(signature)) {
                    return;
                }
                for (JavaParameter parameter : method.getParameters()) {
                    if (!hasAnnotation(parameter, RequestBody.class.getName())) {
                        continue;
                    }
                    boolean valid = hasAnnotation(parameter, "jakarta.validation.Valid")
                            || parameter.getRawType().isAssignableTo(SelfValidatingRequest.class);
                    if (!valid) {
                        events.add(SimpleConditionEvent.violated(method, signature + " recibe @RequestBody sin @Valid ni SelfValidatingRequest"));
                    }
                }
            }
        };
    }

    static ArchRule managementWriteBodiesAreValidated() {
        return methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .and().areDeclaredInClassesThat().resideOutsideOfPackage("..billing..")
                .and(new com.tngtech.archunit.base.DescribedPredicate<JavaMethod>("write mapping") {
                    @Override
                    public boolean test(JavaMethod method) {
                        return method.isAnnotatedWith(PostMapping.class) || method.isAnnotatedWith(PutMapping.class) || method.isAnnotatedWith(PatchMapping.class);
                    }
                })
                .should(validateEveryWriteBody());
    }

    @Test
    void everyManagementWriteBodyIsValidated() {
        managementWriteBodiesAreValidated().check(MAIN_CLASSES);
    }
}

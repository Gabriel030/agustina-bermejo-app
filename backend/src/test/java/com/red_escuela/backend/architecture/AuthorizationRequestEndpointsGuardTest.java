package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class AuthorizationRequestEndpointsGuardTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();

    static ArchRule authorizationEndpointsDeclareRoles() {
        return methods().that().areDeclaredInClassesThat().resideInAPackage("..billing.authorization.controller..")
                .and(new DescribedPredicate<JavaMethod>("mapped endpoint") {
                    @Override
                    public boolean test(JavaMethod method) {
                        return method.isAnnotatedWith(GetMapping.class) || method.isAnnotatedWith(PostMapping.class);
                    }
                })
                .should(new ArchCondition<>("declare @PreAuthorize") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        if (!method.isAnnotatedWith(PreAuthorize.class)) {
                            events.add(SimpleConditionEvent.violated(method, method.getFullName() + " no declara @PreAuthorize"));
                        }
                    }
                });
    }

    @Test
    void everyAuthorizationEndpointDeclaresItsRoles() {
        authorizationEndpointsDeclareRoles().check(MAIN_CLASSES);
    }
}

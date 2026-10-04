package com.red_escuela.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;

import java.math.BigDecimal;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

public final class ArchitectureRules {

    public static final String ROOT = "com.red_escuela.backend";

    static final String MONEY_LIKE_NAME =
            "(?i).*(amount|price|fee|debt|balance|total|monto|cuota|matricula|percent|percentage|rate|grade|score|nota|promedio).*";

    static final String[] FEATURE_PACKAGES = {
            ROOT + ".academicrecord..", ROOT + ".academicstructure..", ROOT + ".announcement..",
            ROOT + ".attendance..", ROOT + ".billing..", ROOT + ".enrollment..", ROOT + ".evaluation..",
            ROOT + ".graduation..", ROOT + ".identity..", ROOT + ".notification.."
    };

    private static final Set<String> PRIMITIVE_NUMBERS = Set.of(
            BigDecimal.class.getName(), Double.class.getName(), Float.class.getName(), "double", "float");

    private static final DescribedPredicate<JavaField> PRIMITIVE_NUMBER_TYPE =
            DescribedPredicate.describe("of a primitive number type",
                    field -> PRIMITIVE_NUMBERS.contains(field.getRawType().getName()));

    private ArchitectureRules() {
    }

    public static ArchRule noPrimitiveMoneyPercentOrGradeFields(String... packages) {
        return noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(packages)
                .and().areDeclaredInClassesThat(DescribedPredicate.describe("not nested", (JavaClass c) -> !c.isNestedClass()))
                .and(PRIMITIVE_NUMBER_TYPE)
                .and().haveNameMatching(MONEY_LIKE_NAME)
                .should().haveNameMatching(".*")
                .because("money must be Money, percentages Percentage and grades Grade (com.red_escuela.backend.shared.domain), never BigDecimal/Double/Float");
    }

    public static ArchRule percentageDtoFieldsEndWithPercentOrRate(String... packages) {
        return fields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(packages)
                .and().haveRawType(ROOT + ".shared.domain.Percentage")
                .should().haveNameMatching("(?i).*(percent|rate)")
                .because("a Percentage exposed in a DTO must be named with a Percent or Rate suffix");
    }

    public static ArchRule sharedDoesNotDependOnFeatures() {
        return noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(FEATURE_PACKAGES)
                .because("shared is the common base and must not depend on any feature");
    }

    public static ArchRule useCaseServicesAreAnnotated() {
        return classes()
                .that().resideInAPackage("..usecase..")
                .and().haveSimpleNameEndingWith("Service")
                .and(DescribedPredicate.describe("top level", (JavaClass c) -> !c.isNestedClass()))
                .should().beAnnotatedWith(ROOT + ".shared.UseCase")
                .because("a *Service in a usecase package is a use case and must carry @UseCase");
    }

    public static ArchRule plainServicesAreNotUseCases() {
        return noClasses()
                .that().resideInAPackage("..service..")
                .should().beAnnotatedWith(ROOT + ".shared.UseCase")
                .because("@UseCase is reserved for classes in a usecase package");
    }
}

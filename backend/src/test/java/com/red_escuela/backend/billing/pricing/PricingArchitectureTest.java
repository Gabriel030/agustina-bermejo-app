package com.red_escuela.backend.billing.pricing;

import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@Tag("D-214")
class PricingArchitectureTest {
    private static final String MUTATORS = "propose|publish|approve|reject|withdraw|expire|reachStart|supersede.*|cancel|set.*";
    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.red_escuela.backend");

    @Test
    void aPriceVersionExposesNoPublicMutatorSoOnlyThePriceAggregateCanMoveIt() {
        methods().that().areDeclaredIn(PriceVersion.class).and().haveNameMatching(MUTATORS).should().notBePublic().check(MAIN_CLASSES);
    }

    @Test
    void onlyThePriceAggregateAndTheVersionItselfCreateOrMoveVersions() {
        noClasses().that().doNotHaveFullyQualifiedName(Price.class.getName()).and().doNotHaveFullyQualifiedName(PriceVersion.class.getName())
                .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>("the target is a mutator of PriceVersion") {
                    @Override
                    public boolean test(JavaMethodCall call) {
                        return call.getTargetOwner().isEquivalentTo(PriceVersion.class) && call.getName().matches(MUTATORS);
                    }
                }).check(MAIN_CLASSES);
    }

    @Test
    void onlyTheNewPricingPackageTouchesThePriceRepositories() {
        noClasses().that().resideOutsideOfPackage("com.red_escuela.backend.billing.pricing..")
                .should().dependOnClassesThat().areAssignableTo(PriceRepository.class)
                .orShould().dependOnClassesThat().areAssignableTo(PriceVersionRepository.class)
                .check(MAIN_CLASSES);
    }

    @Test
    void thePriceRevisionConceptNoLongerExistsInProductionCode() {
        classes().should().haveSimpleNameNotContaining("PriceRevision").check(MAIN_CLASSES);
    }
}

package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModulePatternRulesTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();

    private static final String FIXTURE_ROOT = ArchitectureRules.ROOT + ".architecture.fixture.pattern";

    private static JavaClasses fixturesOf(String rule) {
        return ImportedApplicationClasses.fixturesIn(FIXTURE_ROOT + "." + rule);
    }

    @Test
    void useCasesDoNotDependOnOtherUseCases() {
        FreezingArchRule.freeze(ModulePatternRules.useCasesDoNotDependOnOtherUseCases()).check(MAIN_CLASSES);
    }

    @Test
    void useCaseRuleDetectsAChainedUseCaseAndAllowsItsOwnInterface() {
        assertThatThrownBy(() -> ModulePatternRules.useCasesDoNotDependOnOtherUseCases().check(fixturesOf("r2")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ChainingFlowService")
                .hasMessageContaining("OtherFlowUseCase")
                .hasMessageNotContaining("OwnFlowService");
    }

    @Test
    void specialistsDoNotDependOnUseCases() {
        FreezingArchRule.freeze(ModulePatternRules.specialistsDoNotDependOnUseCases()).check(MAIN_CLASSES);
    }

    @Test
    void specialistRuleDetectsADependencyOnAUseCaseAndAllowsAnIndependentOne() {
        assertThatThrownBy(() -> ModulePatternRules.specialistsDoNotDependOnUseCases().check(fixturesOf("r3")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("InvertedSpecialist")
                .hasMessageNotContaining("IndependentSpecialist");
    }

    @Test
    void controllersCallOnlyUseCasesOfTheirOwnModule() {
        FreezingArchRule.freeze(ModulePatternRules.controllersCallOnlyUseCasesOfTheirOwnModule(
                ArchitectureRules.ROOT, ModulePatternRules.NAMED_CONTROLLER_COLLABORATORS)).check(MAIN_CLASSES);
    }

    @Test
    void controllerRuleDetectsServiceRepositoryAndForeignUseCaseAndAllowsOwnUseCaseAndNamedCollaborator() {
        String root = FIXTURE_ROOT + ".r4";
        Set<String> named = Set.of(root + ".alpha.service.SharedCollaborator");
        assertThatThrownBy(() -> ModulePatternRules.controllersCallOnlyUseCasesOfTheirOwnModule(root, named).check(fixturesOf("r4")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServiceReachingAlphaController")
                .hasMessageContaining("RepositoryReachingAlphaController")
                .hasMessageContaining("ForeignUseCaseCallingAlphaController")
                .hasMessageContaining("OwnServiceReachingAlphaController")
                .hasMessageNotContaining("WellBehavedAlphaController");
    }

    @Test
    void controllerRuleFlagsTheNamedCollaboratorWhenItIsNotListed() {
        String root = FIXTURE_ROOT + ".r4";
        assertThatThrownBy(() -> ModulePatternRules.controllersCallOnlyUseCasesOfTheirOwnModule(root, Set.of()).check(fixturesOf("r4")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("WellBehavedAlphaController");
    }

    @Test
    void apiClassesDoNotDependOnUseCasesNorControllers() {
        FreezingArchRule.freeze(ModulePatternRules.apiClassesDoNotDependOnUseCasesNorControllers()).check(MAIN_CLASSES);
    }

    @Test
    void apiDependencyRuleDetectsUseCaseAndControllerDependenciesAndAllowsASelfContainedClass() {
        assertThatThrownBy(() -> ModulePatternRules.apiClassesDoNotDependOnUseCasesNorControllers().check(fixturesOf("r5")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UpwardLookingFinder")
                .hasMessageContaining("PricingUseCase")
                .hasMessageContaining("PricingController")
                .hasMessageNotContaining("SelfContainedFinder");
    }

    @Test
    void apiMethodsExposeOnlyModelEntities() {
        FreezingArchRule.freeze(ModulePatternRules.apiMethodsExposeOnlyModelEntities()).check(MAIN_CLASSES);
    }

    @Test
    void apiSignatureRuleDetectsAHiddenEntityAndAllowsAModelEntity() {
        assertThatThrownBy(() -> ModulePatternRules.apiMethodsExposeOnlyModelEntities().check(fixturesOf("r6")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyFinder.findAll")
                .hasMessageContaining("LeakyFinder.rename")
                .hasMessageContaining("HiddenEntity")
                .hasMessageNotContaining("ModelAwareFinder");
    }

    @Test
    void apiSignatureRuleDetectsARepositoryAndAllowsAnInternalOne() {
        assertThatThrownBy(() -> ModulePatternRules.apiSignaturesExposeNoRepository("..api..").check(fixturesOf("r8")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RepositoryLeakyFinder.repository")
                .hasMessageContaining("RepositoryLeakyFinder.use")
                .hasMessageNotContaining("InternalRepositoryFinder");
    }

    @Test
    void apiInterfacesAreNamedPortAndImplementedByAnotherModule() {
        FreezingArchRule.freeze(ModulePatternRules.apiInterfacesAreNamedPortAndImplementedByAnotherModule(ArchitectureRules.ROOT))
                .check(MAIN_CLASSES);
    }

    @Test
    void apiInterfaceRuleDetectsUnnamedOrphanAndSameModuleInterfacesAndAllowsAnInvertedPort() {
        assertThatThrownBy(() -> ModulePatternRules.apiInterfacesAreNamedPortAndImplementedByAnotherModule(FIXTURE_ROOT + ".r7").check(fixturesOf("r7")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnnamedLookup is an api interface not named *Port")
                .hasMessageContaining("OrphanPort has no implementation in another module")
                .hasMessageContaining("SameModulePort has no implementation in another module")
                .hasMessageNotContaining("ReleasedLookupPort")
                .hasMessageNotContaining("package-info");
    }
}

package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.BackendApplication;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.springframework.modulith.core.ApplicationModules;

public final class ImportedApplicationClasses {

    private static final String ROOT = "com.red_escuela.backend";
    private static final String FIXTURE_PACKAGE = ROOT + ".architecture.fixture";

    private static final class MainHolder {
        private static final JavaClasses CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    private static final class TestHolder {
        private static final JavaClasses CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    private static final class ModulesHolder {
        private static final ApplicationModules MODULES = ApplicationModules.of(BackendApplication.class);
    }

    private ImportedApplicationClasses() {
    }

    public static JavaClasses main() {
        return MainHolder.CLASSES;
    }

    public static JavaClasses allTests() {
        return TestHolder.CLASSES;
    }

    public static JavaClasses testsOutsideFixtures() {
        return allTests().that(JavaClass.Predicates.resideOutsideOfPackage(FIXTURE_PACKAGE + ".."));
    }

    public static JavaClasses fixturesIn(String packageName) {
        return allTests().that(JavaClass.Predicates.resideInAPackage(packageName + ".."));
    }

    public static ApplicationModules modules() {
        return ModulesHolder.MODULES;
    }
}

package com.red_escuela.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public final class ModulePatternRules {

    static final Set<String> NAMED_CONTROLLER_COLLABORATORS = Set.of(
            ArchitectureRules.ROOT + ".identity.service.CurrentPersonService",
            ArchitectureRules.ROOT + ".identity.service.CurrentStudentService",
            ArchitectureRules.ROOT + ".academicstructure.service.CurrentInstructorService");

    private static final String USE_CASE_ANNOTATION = ArchitectureRules.ROOT + ".shared.UseCase";

    private static final String NAMED_INTERFACE_ANNOTATION = "org.springframework.modulith.NamedInterface";

    private static final String MODEL_INTERFACE_NAME = "model";

    private static final DescribedPredicate<JavaClass> USE_CASE_IMPLEMENTATION = DescribedPredicate.describe(
            "are annotated with @UseCase or are named *Service",
            (JavaClass javaClass) -> javaClass.isAnnotatedWith(USE_CASE_ANNOTATION) || javaClass.getSimpleName().endsWith("Service"));

    private ModulePatternRules() {
    }

    public static ArchRule useCasesDoNotDependOnOtherUseCases() {
        return classes()
                .that().resideInAPackage("..usecase..")
                .and(USE_CASE_IMPLEMENTATION)
                .should(notDependOnAnotherUseCase())
                .allowEmptyShould(true)
                .because("a use case is the script of one flow and calls specialists, never another use case; only its own interface is allowed");
    }

    public static ArchRule specialistsDoNotDependOnUseCases() {
        return noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..usecase..")
                .allowEmptyShould(true)
                .because("a specialist is called by use cases, so depending back on a use case inverts the chain coordinator, use case, specialist, tool");
    }

    public static ArchRule controllersCallOnlyUseCasesOfTheirOwnModule(String root, Set<String> namedCollaborators) {
        return classes()
                .that().resideInAPackage(root + "..controller..")
                .should(callNoServiceRepositoryOrForeignUseCase(root, namedCollaborators))
                .allowEmptyShould(true)
                .because("a controller is a coordinator: it invokes the use cases of its own module and never a service or repository directly; "
                        + "the only shared collaborators allowed are " + new TreeSet<>(namedCollaborators));
    }

    public static ArchRule apiClassesDoNotDependOnUseCasesNorControllers() {
        return noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage("..usecase..", "..controller..")
                .allowEmptyShould(true)
                .because("the api of a module is made of specialists that sit below use cases and controllers in the chain");
    }

    public static ArchRule apiMethodsExposeOnlyModelEntities() {
        return classes()
                .that().resideInAPackage("..api..")
                .should(exposeNoEntityOutsideTheModelInterface())
                .allowEmptyShould(true)
                .because("a public api method takes and returns java types, api records, events and entities marked @NamedInterface(\"model\"), never any other entity");
    }

    public static ArchRule apiSignaturesExposeNoRepository(String apiPackage) {
        return classes()
                .that().resideInAPackage(apiPackage)
                .should(exposeNoRepositoryInPublicSignatures())
                .allowEmptyShould(true)
                .because("a public api member never takes, returns or holds a repository in a public field: repositories are used inside the specialist, never handed to another module");
    }

    public static ArchRule apiInterfacesAreNamedPortAndImplementedByAnotherModule(String root) {
        return classes()
                .that().resideInAPackage("..api..")
                .and().areInterfaces()
                .and().areNotAnnotations()
                .should(bePortImplementedOutsideTheirModule(root))
                .allowEmptyShould(true)
                .because("an interface in api is an inverted port: it is named *Port and implemented by a class of another module");
    }

    static String moduleOf(JavaClass javaClass, String root) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(root + ".")) {
            return "";
        }
        String rest = packageName.substring(root.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static ArchCondition<JavaClass> notDependOnAnotherUseCase() {
        return new ArchCondition<>("not depend on a *UseCase other than their own interface") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Set<String> ownInterfaces = javaClass.getAllRawInterfaces().stream()
                        .map(type -> outerName(type.getName()))
                        .collect(Collectors.toSet());
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    String target = outerName(dependency.getTargetClass().getName());
                    if (dependency.getTargetClass().getPackageName().matches(".*\\.usecase(\\..*)?") && target.endsWith("UseCase") && !ownInterfaces.contains(target) && !target.equals(outerName(javaClass.getName()))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> callNoServiceRepositoryOrForeignUseCase(String root, Set<String> namedCollaborators) {
        return new ArchCondition<>("not depend on a service, a repository or a use case of another module") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String ownModule = moduleOf(javaClass, root);
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getName().startsWith(root + ".") || namedCollaborators.contains(outerName(target.getName()))) {
                        continue;
                    }
                    boolean forbidden = target.getPackageName().matches(".*\\.(service|repository)(\\..*)?")
                            || (target.getPackageName().matches(".*\\.usecase(\\..*)?") && !moduleOf(target, root).equals(ownModule));
                    if (forbidden) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> exposeNoEntityOutsideTheModelInterface() {
        return new ArchCondition<>("expose no entity outside @NamedInterface(\"model\") in public method signatures") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    Stream.concat(
                                    method.getReturnType().getAllInvolvedRawTypes().stream(),
                                    method.getParameterTypes().stream().flatMap(type -> type.getAllInvolvedRawTypes().stream()))
                            .filter(type -> type.getPackageName().matches(".*\\.entity(\\..*)?"))
                            .filter(type -> !isModelEntity(type))
                            .distinct()
                            .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " exposes " + type.getName() + " which is not @NamedInterface(\"model\")")));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> exposeNoRepositoryInPublicSignatures() {
        return new ArchCondition<>("expose no repository in public method signatures or public fields") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    Stream.concat(
                                    method.getReturnType().getAllInvolvedRawTypes().stream(),
                                    method.getParameterTypes().stream().flatMap(type -> type.getAllInvolvedRawTypes().stream()))
                            .filter(type -> type.getPackageName().matches(".*\\.repository(\\..*)?"))
                            .distinct()
                            .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " exposes repository " + type.getName())));
                }
                for (JavaField field : javaClass.getFields()) {
                    if (field.getModifiers().contains(JavaModifier.PUBLIC)
                            && field.getRawType().getPackageName().matches(".*\\.repository(\\..*)?")) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " exposes repository " + field.getRawType().getName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> bePortImplementedOutsideTheirModule(String root) {
        return new ArchCondition<>("be named *Port and have an implementation in another module") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.getSimpleName().equals("package-info")) {
                    return;
                }
                if (!javaClass.getSimpleName().endsWith("Port")) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " is an api interface not named *Port"));
                    return;
                }
                String ownModule = moduleOf(javaClass, root);
                boolean implementedElsewhere = javaClass.getAllSubclasses().stream()
                        .anyMatch(subclass -> !subclass.isInterface() && !moduleOf(subclass, root).equals(ownModule));
                if (!implementedElsewhere) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " has no implementation in another module"));
                }
            }
        };
    }

    private static boolean isModelEntity(JavaClass type) {
        return type.getAnnotations().stream()
                .filter(annotation -> annotation.getRawType().getName().equals(NAMED_INTERFACE_ANNOTATION))
                .anyMatch(ModulePatternRules::declaresModelInterface);
    }

    private static boolean declaresModelInterface(JavaAnnotation<?> annotation) {
        Object value = annotation.get("value").orElse(null);
        if (value instanceof Object[] names) {
            return Arrays.asList(names).contains(MODEL_INTERFACE_NAME);
        }
        return MODEL_INTERFACE_NAME.equals(value);
    }

    private static String outerName(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }
}

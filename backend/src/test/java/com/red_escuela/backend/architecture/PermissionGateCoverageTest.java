package com.red_escuela.backend.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionGateCoverageTest {

    private static final String ROOT = "com.red_escuela.backend";
    private static final String GATE_EXPRESSION = "@permissionGate.allows(";

    private static final Set<String> MODULES_PENDING_MIGRATION = Set.of(
            "academicrecord", "academicstructure", "billing", "enrollment", "evaluation",
            "graduation", "identity", "notification", "platform");

    private static final Set<String> CONTROLLERS_FIXED_BY_ROLE = Set.of(
            ROOT + ".identity.controller.PermissionController");

    private static final Set<String> CONTROLLERS_WITHOUT_ROLE_RULE = Set.of(
            ROOT + ".announcement.controller.MeAnnouncementController");

    @Test
    void everyProtectedEndpointUsesTheGateOrIsExempt() throws Exception {
        List<String> uncovered = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            if (CONTROLLERS_FIXED_BY_ROLE.contains(controller.getName()) || CONTROLLERS_WITHOUT_ROLE_RULE.contains(controller.getName()) || MODULES_PENDING_MIGRATION.contains(moduleOf(controller))) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                if (!usesGate(method, controller)) {
                    uncovered.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(uncovered).as("endpoints without the permission gate and outside the exempt lists").isEmpty();
    }

    @Test
    void permissionEndpointsStayFixedToTheDirectorRoleAndNeverUseTheGrid() throws Exception {
        Class<?> controller = Class.forName(ROOT + ".identity.controller.PermissionController");
        int checked = 0;
        for (Method method : controller.getDeclaredMethods()) {
            if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                continue;
            }
            PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            assertThat(preAuthorize).as(method.getName()).isNotNull();
            String expectedRule = Set.of("getCatalog", "getGrid").contains(method.getName())
                    ? "hasAnyRole('DIRECTOR', 'SUBDIRECTOR')"
                    : "hasRole('DIRECTOR')";
            assertThat(preAuthorize.value()).isEqualTo(expectedRule);
            checked++;
        }
        assertThat(checked).isEqualTo(6);
    }

    @Test
    void exemptListsOnlyNameExistingModulesAndControllers() throws Exception {
        for (String controllerName : Stream.concat(CONTROLLERS_FIXED_BY_ROLE.stream(), CONTROLLERS_WITHOUT_ROLE_RULE.stream()).toList()) {
            assertThat(Class.forName(controllerName)).isNotNull();
        }
        Set<String> present = new HashSet<>();
        controllers().forEach(controller -> present.add(moduleOf(controller)));
        assertThat(present).containsAll(MODULES_PENDING_MIGRATION);
    }

    private static boolean usesGate(Method method, Class<?> controller) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        PreAuthorize effective = onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        return effective != null && effective.value().contains(GATE_EXPRESSION);
    }

    private static String moduleOf(Class<?> type) {
        return type.getPackageName().substring(ROOT.length() + 1).split("\\.")[0];
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            boolean fromMainClasses = type.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("/classes/");
            if (fromMainClasses && type.getPackageName().startsWith(ROOT + ".")) {
                classes.add(type);
            }
        }
        return classes;
    }
}

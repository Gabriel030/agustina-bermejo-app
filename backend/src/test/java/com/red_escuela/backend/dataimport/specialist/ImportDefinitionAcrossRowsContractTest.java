package com.red_escuela.backend.dataimport.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

class ImportDefinitionAcrossRowsContractTest {
    @Test
    void everyDefinitionOverridingValidateAcrossRowsDeclaresItDoesSo() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ImportEntityDefinition.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.red_escuela.backend.dataimport");
        assertThat(candidates).isNotEmpty();
        List<String> violations = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            boolean overrides = type.getMethod("validateAcrossRows", List.class).getDeclaringClass() != ImportEntityDefinition.class;
            if (overrides && !declaresAcrossRows(type)) {
                violations.add(type.getSimpleName());
            }
        }
        assertThat(violations).isEmpty();
    }

    private static boolean declaresAcrossRows(Class<?> type) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(new Object[constructor.getParameterCount()]);
        return (boolean) type.getMethod("validatesAcrossRows").invoke(instance);
    }
}

package com.red_escuela.backend.shared.module;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.shared.domain.Money;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

class CatalogPricedCoverageTest {

    @Test
    void everyResponseWithAMoneyFieldInAHidingControllerIsCatalogPriced() throws Exception {
        List<String> uncovered = new ArrayList<>();
        for (Class<?> controller : hidingControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                Class<?> responseType = unwrap(ResolvableType.forMethodReturnType(method, controller)).resolve(Object.class);
                if (hasMoneyField(responseType) && !CatalogPriced.class.isAssignableFrom(responseType)) {
                    uncovered.add(controller.getSimpleName() + "." + method.getName() + " -> " + responseType.getSimpleName());
                }
            }
        }

        assertThat(uncovered).isEmpty();
    }

    @Test
    void hidingControllersExist() throws Exception {
        assertThat(hidingControllers()).isNotEmpty();
    }

    private static List<Class<?>> hidingControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(HidesPricesWhenBillingOff.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.red_escuela.backend")) {
            classes.add(Class.forName(definition.getBeanClassName()));
        }
        return classes;
    }

    private static ResolvableType unwrap(ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        boolean wrapper = ResponseEntity.class.isAssignableFrom(raw) || Page.class.isAssignableFrom(raw)
                || Collection.class.isAssignableFrom(raw) || Optional.class.isAssignableFrom(raw);
        return wrapper ? unwrap(type.getGeneric(0)) : type;
    }

    private static boolean hasMoneyField(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> Money.class.equals(field.getType()));
    }
}

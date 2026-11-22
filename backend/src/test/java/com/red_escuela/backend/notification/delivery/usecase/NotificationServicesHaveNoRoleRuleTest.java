package com.red_escuela.backend.notification.delivery.usecase;

import com.red_escuela.backend.notification.reporting.usecase.GetCollectionReminderMetricService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServicesHaveNoRoleRuleTest {

    @Test
    void serviceBehindAGridPermissionDoesNotCarryItsOwnRoleRule() {
        for (Class<?> service : List.of(ListSimulatedEmailsService.class, GetCollectionReminderMetricService.class)) {
            assertThat(AnnotatedElementUtils.findMergedAnnotation(service, PreAuthorize.class)).as(service.getSimpleName()).isNull();
            for (Method method : service.getDeclaredMethods()) {
                assertThat(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class)).as(service.getSimpleName() + "#" + method.getName()).isNull();
            }
        }
    }
}

package com.red_escuela.backend.testsupport;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AutoConfigureMockMvc
@AutoConfigureObservability
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "auth.rate-limit.login.capacity=2",
        "auth.rate-limit.login.refill-per-minute=2",
        "auth.rate-limit.forgot-password.capacity=2",
        "auth.rate-limit.forgot-password.refill-per-minute=2",
        "management.security.user=scraper",
        "management.security.password=local-test"
})
public @interface SharedPropertyOverrides {
}

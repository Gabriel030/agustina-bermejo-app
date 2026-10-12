package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.shared.mail.EmailService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MockitoBean(types = {EmailService.class, GoogleIdTokenService.class})
public @interface SharedMockBeans {
}

package com.red_escuela.backend.testsupport;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Tag("slow")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SlowTest {

    String reason();
}

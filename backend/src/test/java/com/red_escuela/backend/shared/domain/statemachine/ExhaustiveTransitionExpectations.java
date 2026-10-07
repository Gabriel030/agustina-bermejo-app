package com.red_escuela.backend.shared.domain.statemachine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExhaustiveTransitionExpectations {

    Class<?> machine();
}

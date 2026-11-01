package com.red_escuela.backend.shared.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresModule {

    String BILLING = "billing";

    String value();

    BillingMode minMode() default BillingMode.MIRROR;
}

package com.red_escuela.backend.shared.permission;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@PreAuthorize("@permissionGate.allows(authentication, '{permission}', '{legacyRoles}')")
public @interface RequiresProbePermission {

    String permission();

    String legacyRoles();
}

/**
 * <b>Qué es</b>: la compuerta de permisos por rol que convive con los {@code @PreAuthorize} históricos mientras cada módulo migra.
 *
 * <p><b>Expone</b>: {@code PermissionGate}, {@code @RequiresPermission}, {@code PermissionPolicyStore} y {@code PermissionDiscrepancySink}.
 *
 * <p><b>No hacer</b>: leer tablas de otro módulo; decidir propiedad de recursos; delegar permisos fijos del Director.
 *
 * <p><b>Ejemplo</b>: {@code @RequiresPermission(code = "announcement.create", legacyRoles = "ROLE_DOCENTE")}.
 */
package com.red_escuela.backend.shared.permission;

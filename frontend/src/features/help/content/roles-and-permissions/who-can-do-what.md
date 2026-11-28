---
title: Saber quién puede hacer qué
section: roles-and-permissions
roles: [alumno, docente, preceptor, administrativo, subdirector, director, platform_admin]
status: borrador
screens: []
last_review: 2026-11-28
---

## Para qué sirve

Para saber qué puede hacer cada uno de los 7 roles del sistema y a quién pedirle lo que no podés hacer vos. El resumen sale de lo que cada rol ve en su menú y de las reglas de cada pantalla.

## Quién puede

Todos los roles pueden consultarla: Alumno, Docente, Preceptor, Administrativo, Subdirector, Director y Admin de plataforma.

## Pasos

1. Mirá el menú de tu rol: lo que ves es lo que tu rol puede abrir.
2. Si necesitás algo que no está en tu menú, buscalo en la lista de abajo y fijate qué rol lo hace.
3. Si lo hace otro rol, pedíselo a esa persona.
4. Si sos Director o Subdirector y querés ver el detalle por permiso, abrí «Roles y permisos» (ver «Asignar roles y permisos»).

## Reglas

**Qué hace cada rol, según su menú**

- Alumno: ve su calendario, sus cursos, sus carreras, sus certificados, sus pagos, la oferta académica y las reuniones informativas.
- Docente: ve sus comisiones, toma asistencia y ve las mesas de examen.
- Preceptor: gestiona alumnos, solicitudes de ingreso, listas de espera, oferta académica, mesas de examen, solicitudes de asistencia y períodos.
- Administrativo: gestiona alumnos, el personal (docentes y preceptores), solicitudes de ingreso, listas de espera, mesas de examen, comunicaciones, invitaciones, cierres de comisión, egresos, constancias, solicitudes de asistencia, feriados, períodos, gestión académica, facturación y aprobaciones.
- Subdirector: hace lo mismo que el Administrativo, salvo emitir constancias por excepción, y además puede ver la grilla de «Roles y permisos» sin modificarla.
- Director: tiene la gestión general. Aprueba lo que le piden las otras áreas y es el único que modifica la grilla de «Roles y permisos». También maneja los ajustes del instituto, la importación inicial, la auditoría y los backups.
- Admin de plataforma: activa módulos, crea el primer director y consulta auditoría y backups.

**Lo que solo hace un rol**

- Modificar la grilla de «Roles y permisos»: solo el Director.
- Aprobar o rechazar pedidos de autorización: solo el Director, y nunca los que pidió él mismo.
- Dar de alta a un Administrativo o a un Subdirector: solo el Director.
- Dar de alta a un Docente o a un Preceptor: el Administrativo y el Subdirector.
- Crear el primer director: solo el Admin de plataforma.

**Cómo se define cada permiso**

La grilla de «Roles y permisos» define qué puede hacer cada rol en cada módulo. Cada rol, en cada módulo, está en uno de tres modos: Legado, Sombra o Activo. En Legado y en Sombra manda la regla vieja. En Activo manda lo que dice la grilla. El Director siempre tiene todos los permisos.

> Pendiente de verificar: qué roles y módulos están hoy en modo Activo en producción. Donde haya modo Activo, el permiso real puede diferir de esta lista.

## Qué pasa después

Sabés a quién pedirle cada cosa. Si te falta una pantalla, no es un error: es que tu rol no la tiene.

## Errores comunes

- «No autorizado: Tu usuario no tiene permiso para acceder a esta sección.» Intentaste abrir una pantalla de otro rol.
- «Solo la dirección puede aprobar pedidos.» Aprobar es del Director.
- «No tenés permiso para dar de alta personal con ese rol.» Cada rol solo da de alta ciertos roles (ver «Dar de alta y gestionar al personal»).
- Creer que el Subdirector puede cambiar permisos. Solo los puede ver.

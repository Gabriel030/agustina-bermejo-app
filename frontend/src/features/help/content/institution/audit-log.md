---
title: Consultar la auditoría
section: institution
roles: [director, platform_admin]
status: borrador
screens: ["auditoria"]
last_review: 2026-11-28
---

## Para qué sirve

Para ver quién hizo qué y cuándo en el sistema: bajas, cambios de comisión, reseteos de clave, importaciones, cambios de permisos, constancias, envíos y más. Cada evento guarda el valor de antes y el de después.

## Quién puede

Director y administrador de la plataforma. Se entra desde el menú Institución (Director) o desde «Auditoría» (plataforma). Cada uno ve su propio registro.

## Pasos

1. Entrá a «Auditoría». Ves la lista de eventos con «Fecha y hora», «Actor», «Rol», «Acción», «Entidad» y «Motivo».
2. Para acotar, usá los filtros: «Acción», «Rol», «Entidad», «ID de entidad», «Actor: nombre o ID» y las fechas «Desde» y «Hasta». En el celular, tocá «Filtros» para verlos.
3. El filtro «ID de entidad» se habilita al elegir una entidad.
4. Para sacar todos los filtros, tocá «Limpiar filtros».
5. Tocá un evento para abrir el detalle. Ves quién lo hizo, cuándo, el motivo y los bloques «Antes» y «Después», con un resumen de los cambios.
6. Usá el paginador de abajo para ver más eventos. El total aparece como «N eventos».

## Reglas

- La auditoría es de solo lectura: no se puede editar ni borrar nada.
- Para buscar por actor alcanza con el nombre (mínimo 2 letras) o con el número de persona.
- La fecha «Desde» no puede ser posterior a «Hasta».
- En el detalle, las contraseñas, tokens y hashes se ocultan antes de mostrarse.
- Se muestra el rol de quien actuó: Director, Subdirector, Administrativo, Preceptor, Docente, Alumno, Plataforma o Sistema.

## Qué pasa después

- Consultar la auditoría no cambia nada en el sistema.
- Los eventos más usados: bajas y reactivaciones, cambios de comisión, importaciones, envíos de comunicaciones, cambios de permisos, constancias y certificados, listas de espera y pagos en línea.

## Errores comunes

- «Ningún evento coincide»: probá con otros filtros o tocá «Limpiar filtros».
- «Todavía no hay actividad registrada»: el sistema aún no guardó eventos.
- «No tenés acceso a esta pantalla»: la auditoría está disponible solo para Dirección o para el dueño de la plataforma, según el caso.
- Un código de acción sin traducir aparece tal cual: se muestra el código si el evento es de un tipo nuevo.

**Pendiente de verificar:**

- Qué eventos ve cada uno: el código usa un registro distinto para la plataforma y otro para el Director, pero no hay prueba en pantalla de qué diferencia hay entre ambos.
- Por cuánto tiempo se conservan los eventos.

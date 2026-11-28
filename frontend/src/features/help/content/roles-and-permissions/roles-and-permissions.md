---
title: Asignar roles y permisos
section: roles-and-permissions
roles: [director, subdirector]
status: borrador
screens: ["permisos"]
last_review: 2026-11-28
---

## Para qué sirve

Para definir qué puede hacer cada rol en cada módulo del sistema, desde la pantalla «Roles y permisos». Es una grilla: en las filas están los permisos agrupados por módulo y en las columnas, los roles.

Esta pantalla no le asigna un rol a una persona. Los roles de las personas se asignan al darlas de alta (ver «Dar de alta y gestionar al personal»).

## Quién puede

- Director: ve y modifica la grilla.
- Subdirector: la ve en modo lectura. No puede modificarla.

## Pasos

**Mirar la grilla**

1. Entrá a «Roles y permisos». El Director la encuentra en el grupo «Institución». El Subdirector la ve como una entrada del menú.
2. En la solapa «Permisos», tocá un módulo para abrirlo. Con «Expandir todo» abrís todos y con «Contraer todo» los cerrás.
3. Para buscar, escribí en «Buscar un permiso» (por ejemplo, «reembolsos»).
4. Para ver un solo módulo, usá «Todos los módulos». Para ver solo algunos roles, usá «Mostrar solo:» y tocá el rol. «Limpiar» saca el filtro.
5. Tocá «¿Qué significan Legado, Sombra y Activo?» si necesitás recordar los modos.

**Cambiar qué puede hacer un rol (solo el Director)**

1. Abrí el módulo y tildá o destildá la casilla del rol en el permiso que quieras cambiar.
2. Abajo aparece la barra «N cambios sin guardar». Tocá «Revisar y guardar». Con «Descartar» los deshacés.
3. En «Revisar N cambios antes de guardar», escribí el «Motivo del cambio».
4. Si hay permisos sensibles, aparece «Confirmación reforzada». Tildá «Entiendo que delego un permiso sensible». En ese caso el motivo es obligatorio.
5. Tocá «Guardar N cambios». Para seguir editando, tocá «Seguir editando».
6. Aparece «Guardamos N cambios. Quedaron en la auditoría.»

**Cambiar el modo de un rol en un módulo (solo el Director)**

1. En la fila del módulo, tocá la etiqueta del modo (Legado, Sombra o Activo) del rol.
2. En «Modo de… en…», elegí el modo nuevo.
3. Escribí el «Motivo».
4. Si pasás a Activo y el módulo tiene discrepancias, tildá «Entiendo que… accesos hoy darían un resultado distinto».
5. Tocá «Cambiar modo».

**Revisar discrepancias (solo el Director)**

1. Abrí la solapa «Discrepancias».
2. Elegí un módulo con «Todos los módulos» si querés filtrar.
3. Tocá «Ver en la grilla» para ir al módulo.

## Reglas

- La grilla muestra 5 roles: Director, Subdirector, Administrativo, Preceptor y Docente. Alumno y Admin de plataforma no aparecen.
- El Director siempre tiene todos los permisos: su columna dice «Todo» y no se edita.
- Algunos permisos son exclusivos del Director. Aparecen con un guion y no se pueden delegar.
- El motivo del cambio tiene entre 10 y 500 caracteres. Todo cambio queda en la auditoría.
- Los modos significan:
  - Legado: manda la regla vieja. Lo que marques en la grilla se guarda, pero todavía no se aplica.
  - Sombra: sigue mandando la regla vieja. El sistema compara cada acceso con la grilla y anota las diferencias.
  - Activo: manda la grilla. La regla vieja deja de usarse para ese rol y ese módulo.
- Volver a Legado es seguro: la grilla se conserva para retomarla después.
- Una discrepancia es un momento en que la regla vieja y la grilla darían respuestas distintas. Se registran en los módulos en modo Sombra. Antes de pasar un módulo a Activo conviene revisar que no queden.
- La solapa «Discrepancias» solo la ve quien puede editar.

## Qué pasa después

El cambio queda guardado y registrado en la auditoría con su motivo. Solo se aplica en los módulos que están en modo Activo para ese rol. En Legado se guarda sin efecto, y en Sombra solo se compara.

## Errores comunes

- «Estás viendo la grilla en modo lectura.» Sos Subdirector. Si necesitás un cambio, pedíselo al Director.
- «No tenés acceso a esta pantalla.» Es para el Director, y el Subdirector puede verla sin editarla.
- «No se guardó nada. No tenés permiso para cambiar la grilla.» Solo el Director puede editarla.
- «No pudimos guardar todo.» Puede que se hayan guardado algunos cambios y otros no. El mensaje indica cuántos quedaron pendientes: reintentá con esos.
- «Escribí un motivo de al menos 10 caracteres.» Ampliá el motivo.
- «No hay permisos que coincidan.» Tocá «Limpiar filtros».
- «Todavía no hay permisos cargados.» El catálogo de permisos está vacío.

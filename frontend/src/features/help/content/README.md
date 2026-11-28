# Contenido del manual de usuario

Este árbol es el texto de la página `/ayuda` (épica #1721). Es Markdown versionado con la app. No es código: el cargador (rebanada B) lo lee con la importación cruda de Vite y solo muestra las tareas con `status: publicado`.

## Estructura

Una carpeta por sección del manual y un archivo por tarea:

```
content/
  first-day/            Primer día en la app (cuenta, perfil, roles)
  roles-and-permissions/ Quién puede qué, permisos, autorizaciones, personal
  set-up-school-year/   Armar el año (carreras, materias, ciclo, cohorte, comisiones)
  monthly-and-yearly/   Qué se hace cada mes y cada año
  students/             Alumnos, matrícula, asistencia, notas
  exams-and-graduation/ Mesas de examen, egresos, certificados y constancias
  data-import/          Importación inicial
  notices-and-invitations/ Comunicaciones, avisos de comisión e invitaciones
  institution/          Ajustes, auditoría, backups, módulos y primer director
  billing/              Cobranza: pendiente de rediseño
  _plantilla.md         Plantilla vacía de una tarea (no se publica)
  INVENTARIO.md         Pantallas y rutas por rol, con la tarea que las cubre
  README.md             Esta guía
```

Las carpetas usan nombres en inglés por la regla de nombres de archivo. Equivalen a las secciones propuestas en el Issue (`primer-dia`, `permisos-y-roles`, `armar-el-anio`, `mes-y-anio`, `alumnos`, `mesas-y-egreso`, `importacion`, `avisos-e-invitaciones`, `institucion`, `cobranza`).

El cargador ignora `_plantilla.md`, `README.md` e `INVENTARIO.md`.

## Front-matter

Cada tarea empieza con este bloque:

- `title`: título de la tarea, formulado como acción («Abrir una cohorte», no «Cohortes»).
- `section`: carpeta a la que pertenece.
- `roles`: roles a los que aplica. Valores válidos: `alumno`, `docente`, `preceptor`, `administrativo`, `subdirector`, `director`, `platform_admin`.
- `status`: `borrador` o `publicado`. Todo texto entra como `borrador`. Pasa a `publicado` con el OK de Gabriel.
- `screens`: rutas o pantallas del menú que cubre, tal como aparecen en `INVENTARIO.md`.
- `last_review`: fecha de la última revisión (AAAA-MM-DD).

## Plantilla de tarea

Todas las tareas tienen estos seis títulos, en este orden:

1. Para qué sirve
2. Quién puede
3. Pasos
4. Reglas
5. Qué pasa después
6. Errores comunes

La excepción es `billing/billing-pending-redesign.md`, que solo lleva el aviso de rediseño y la lista de pantallas cubiertas.

## Reglas de redacción

- Castellano claro, en lenguaje de secretaría. Se usan los nombres exactos de menús y botones que la persona ve en pantalla.
- Pasos numerados, uno por acción. «Quién puede» nombra roles, no permisos sueltos.
- Sin jerga técnica, sin códigos, sin rutas internas.
- Sin usuarios de prueba, contraseñas ni datos reales de personas.
- Sin capturas hasta el último paquete. Los pasos se entienden sin imagen.
- Las reglas de negocio se verifican contra el código, no contra el manual anterior. El manual viejo solo aporta texto reescribible.

## Estados

- `borrador`: se escribe y se revisa. No aparece en la app de producción.
- `publicado`: validado por Gabriel. Es lo único que muestra `/ayuda`.

## Agregar o cambiar una tarea

1. Copiar `_plantilla.md` a la carpeta de la sección con un nombre en kebab-case en inglés.
2. Completar el front-matter y dejar `status: borrador`.
3. Si la pantalla ya existe en `INVENTARIO.md`, usar su fila. Si es nueva, agregarla al inventario con su tarea.
4. Un PR que cambia una pantalla actualiza su tarea o la crea como `borrador`.

---
title: Versionar el plan de estudios
section: set-up-school-year
roles: [administrativo, subdirector]
status: borrador
screens: ["gestion-academica/carreras/:id"]
last_review: 2026-11-28
---

## Para qué sirve

El plan de estudios es la lista de materias de una carrera, ordenadas por año y cuatrimestre, con sus correlativas. Cada cohorte cursa con un plan. Esta tarea explica cómo armar y mantener esa lista.

## Quién puede

Administrativo y Subdirector. Entran desde «Gestión académica», solapa «Carreras», abriendo la carrera. El plan está en la solapa «Plan de estudios».

## Pasos

Agregar una materia al plan:

1. Abrí la carrera. En «Plan de estudios» hay un bloque por año.
2. Tocá «Agregar materia», arriba o dentro del año que corresponda.
3. En el panel «Agregar materia», buscá la materia en «Buscar materia activa…» y elegila.
4. Elegí la «Categoría»: Obligatoria, Obligatoria adicional o Electiva.
5. Completá el año, el «Cuatrimestre» (1.° cuatrimestre, 2.° cuatrimestre, Anual o Sin especificar) y los créditos. Si la categoría es Electiva, elegí el «Grupo electivo».
6. Tocá «Agregar». Aparece «Materia agregada al plan».

Definir correlativas:

1. En la fila de la materia tocá «Correlativas».
2. En el panel «Correlativas y co-requisitos», en «Nueva correlativa», elegí la «Materia correlativa» y la «Condición»: Aprobada, o Cursada regular o aprobada. Si querés, indicá la «Nota mínima (opcional)».
3. Tocá «Agregar». Aparece «Correlativa agregada».
4. Para un co-requisito, usá «Nuevo co-requisito», elegí la materia y tocá «Agregar».
5. Para quitar una correlativa o un co-requisito, usá el botón de papelera de su fila.

Quitar una materia: en su fila tocá «Quitar del plan» y confirmá con «Quitar del plan». La materia queda inactiva en el plan. Para recuperarla, usá «Volver a agregar al plan».

## Reglas

- Solo se agregan materias activas del banco. Una materia que el director todavía no aprobó no entra al plan.
- Una materia no puede estar dos veces en el mismo plan.
- Una materia obligatoria necesita año y cuatrimestre. Una electiva necesita el grupo electivo, que tiene que estar asignado a la carrera.
- El año tiene que estar entre 1 y la duración de la carrera.
- Los créditos son obligatorios al agregar la materia.
- Una materia no se puede desactivar mientras sea correlativa de otra que sigue activa. Primero se quita la correlatividad.
- Una cohorte solo se puede abrir si su plan de estudios está vigente.
- El plan de una cohorte se fija al crearla. La pantalla de cohortes no permite cambiarlo.

## Qué pasa después

Las materias del primer año del plan son las que se controlan al abrir una cohorte: cada una necesita comisión aprobada. Ver «Abrir una cohorte».

## Errores comunes

- «La materia todavía no está aprobada y no se puede agregar al plan»: pedile al director que la apruebe. Ver «Crear una materia y un curso».
- «Esa materia ya forma parte del plan de esta carrera».
- «Faltan datos que exige la categoría elegida»: completá año, cuatrimestre o grupo según la categoría.
- «La materia es correlativa de otra que sigue activa»: quitá primero la correlatividad.

**Pendiente de verificar:** la versión del plan de estudios (crear una versión nueva, su vigencia y su cierre). El sistema tiene esas funciones, pero la app no tiene pantalla ni botón de «nueva versión». Cuando un plan tiene alumnos matriculados no se puede editar y el sistema indica «Creá una versión nueva del plan». Falta definir con Gabriel cómo se explica o si hay que pedirlo a soporte. Por eso el título habla de versionar y el contenido todavía no lo cubre.

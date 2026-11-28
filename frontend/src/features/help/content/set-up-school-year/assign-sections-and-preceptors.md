---
title: Crear comisiones y asignar preceptores
section: set-up-school-year
roles: [preceptor, director]
status: borrador
screens: ["oferta-academica/nuevo-ciclo", "oferta-academica/carreras/:programaId", "oferta-academica/cursos/:cursoId"]
last_review: 2026-11-28
---

## Para qué sirve

Una comisión es un grupo de cursada de una materia, con su docente titular, su aula, su horario y su cupo. Se arman dentro de un ciclo de oferta: un período más una carrera o un curso. Después cada comisión necesita al menos un preceptor. Sin eso, la cohorte no se puede abrir.

## Quién puede

- Preceptor: arma el ciclo y sus comisiones, y lo envía a aprobación.
- Director: asigna y cambia los preceptores de cada comisión de una carrera. El Director no inicia altas de ciclo.

El Preceptor ve las comisiones y sus preceptores, pero no tiene el botón para asignarlos.

## Pasos

Armar el ciclo y las comisiones (Preceptor):

1. Entrá a «Oferta académica» y tocá «Iniciar alta de ciclo». También lo encontrás dentro de la ficha de una carrera o de un curso.
2. En el paso «Período» elegí el «Período académico», el «Tipo» (Carrera o Curso) y la carrera o el curso.
3. Tocá «Crear borrador y continuar».
4. En el paso «Comisiones» aparece cada materia. Tocá «Agregar comisión para» seguido del nombre de la materia.
5. Completá «Nombre / turno», «Cupo» y «Docente titular».
6. Elegí el «Día», el «Horario inicio» y el «Horario fin».
7. Elegí el «Aula».
8. En «Régimen de asistencia» elegí «Sin límite» o «Con límite de ausentismo» y, en ese caso, el porcentaje máximo.
9. Tocá «Agregar comisión». Repetí con cada comisión. Podés dejar sin comisión las materias que no se dictan en el período.
10. Tocá «Ver resumen» con la cantidad de comisiones.
11. Revisá el resumen y tocá «Enviar a aprobación».

Para chequear qué aula está libre antes de cargar el horario, usá «Calendario» dentro de la carrera. Muestra la disponibilidad de aulas por período, día, turno y módulos.

Retomar un ciclo: si quedó «En construcción» o «Rechazado», en la carrera o el curso aparece «Continuar armado» o «Corregir y reenviar».

Asignar preceptores a una comisión (Director):

1. Entrá a «Oferta académica», solapa «Carreras», y abrí la carrera.
2. En el ciclo, buscá la comisión. Si no tiene preceptor, dice «Sin preceptor».
3. Tocá «Asignar preceptor» (o «Preceptores» si ya tiene).
4. En el selector «Preceptores» buscá por nombre y elegí uno o más.
5. Mirá los avisos. «Queda sin preceptor. Las solicitudes nuevas llegan a Dirección.» aparece si dejás la lista vacía. «La comisión tiene N solicitudes pendientes; pasan a Dirección.» aparece si sacás a un preceptor que las tenía.
6. Tocá «Guardar». Si falla, el botón pasa a «Reintentar» y tus cambios siguen ahí.

## Reglas

- Para guardar una comisión hay que completar nombre, cupo, docente y aula. La hora de fin tiene que ser posterior a la de inicio.
- El cupo no puede superar la capacidad del aula.
- El aula no puede estar ocupada ese día y en ese horario durante el período. Al aprobar, Dirección también ve los cruces de docente.
- El régimen de asistencia es obligatorio: hay que elegir una de las dos opciones. Si es con límite, hay que cargar el porcentaje.
- No se abren comisiones de una materia en borrador. La materia tiene que estar aprobada por el director.
- Para enviar a aprobación, todas las comisiones tienen que tener docente titular. Hay que tener al menos una comisión para ver el resumen.
- Un ciclo enviado a aprobación ya no se modifica. Solo se vuelve a editar si Dirección lo rechaza.
- Cada comisión tiene que terminar con al menos un preceptor. Es un requisito de la apertura de cohorte. Ver «Abrir una cohorte».
- En un ciclo de un curso, el sistema no deja aprobar si hay comisiones sin preceptor.
- Los ciclos y comisiones solo se crean en períodos activos y vigentes.
- Dirección edita una comisión a la vez: las demás quedan bloqueadas con el aviso «Guardá o cancelá la edición abierta».

## Qué pasa después

El ciclo enviado queda «En revisión» y le llega al Director en «Aprobaciones». Cuando lo aprueba, las comisiones se activan. Ver «Aprobar la oferta del ciclo». Los preceptores asignados reciben las solicitudes de sus comisiones y completan el punto «Preceptores en comisiones» del checklist de la cohorte.

## Errores comunes

- «El aula ya está ocupada ese día y en ese horario durante este período.»
- «El cupo supera la capacidad del aula.»
- «La materia todavía es un borrador: el director tiene que aprobarla antes de abrir comisiones.»
- «No se pudo enviar a aprobación. Verificá que todas las comisiones tengan docente titular.»
- «Sección creada pero no se pudo asignar el docente. Podés asignarlo después.»
- «Este ciclo ya fue enviado a aprobación y no se puede modificar.»
- «Ya hay un ciclo en construcción o en revisión para este período.»: hay que retomar el que existe desde el detalle de la carrera o del curso.
- «No pudimos guardar. Tus cambios siguen acá. Reintentá.», al guardar preceptores.

**Pendiente de verificar:**
- La app no muestra un selector de preceptores en las comisiones de un curso. El sistema exige preceptor en cada comisión de curso para aprobar. Hay que confirmar dónde se asignan.
- El Administrativo y el Subdirector figuran con permiso para asignar preceptores, pero la ruta de la oferta académica solo está disponible para Preceptor y Director. Se describió solo el Director.
- Si falla la aprobación por comisiones de curso sin preceptor, la pantalla del Director muestra un mensaje genérico en lugar del texto específico.

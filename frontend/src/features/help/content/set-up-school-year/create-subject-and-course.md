---
title: Crear una materia y un curso
section: set-up-school-year
roles: [administrativo, subdirector, director]
status: borrador
screens: ["gestion-academica", "gestion-academica/nueva", "gestion-academica/:id", "gestion-academica/cursos/nueva", "gestion-academica/cursos/:id", "gestion-academica/cursos/:id/editar"]
last_review: 2026-11-28
---

## Para qué sirve

La materia es la unidad que se suma al plan de una carrera. Se crea en el banco de materias y necesita la aprobación del director antes de poder usarse. El curso es una oferta aparte, con su propio cupo, cuota y temario, que no depende de una carrera.

## Quién puede

- Administrativo y Subdirector: crean y editan materias y cursos. Entran por «Gestión académica».
- Director: aprueba o rechaza las materias. En «Gestión académica» ve la lista «Pendientes de aprobación».

## Pasos

Crear una materia (Administrativo o Subdirector):

1. Entrá a «Gestión académica». La solapa «Banco de materias» es la primera.
2. Tocá «Nueva materia».
3. Paso «Identificación»: completá «Código» (hasta 4 caracteres) y «Nombre».
4. Paso «Detalle programático»: cargá las «Horas semanales teóricas» o las «Horas semanales prácticas». Alcanza con una de las dos. Podés sumar una «Descripción».
5. Paso «Dictado y evaluación»: elegí la «Modalidad de calificación» (Numérica 1–10 o Aprobado / Desaprobado). Si querés, indicá los «Intentos máximos de examen final».
6. Tocá «Guardar borrador». Si falta algo, debajo del botón dice «Para guardar falta completar:» y lo que falta.
7. En el paso «Aprobación» tocá «Enviar a aprobación».

Aprobar una materia (Director):

1. Entrá a «Gestión académica». Aparece «Pendientes de aprobación».
2. En la fila de la materia tocá «Aprobar». Aparece «Materia aprobada».
3. Para rechazarla tocá «Rechazar», escribí el motivo y confirmá. Administración ve ese motivo en la materia rechazada y puede corregirla y volver a enviarla con «Volver a enviar a aprobación».

Dar de baja o reactivar una materia: en su ficha, «Dar de baja» y «Reactivar». Una materia dada de baja pasa a la solapa «Inactivas» del banco.

Crear un curso (Administrativo o Subdirector):

1. En «Gestión académica» elegí la solapa «Cursos» y tocá «Nuevo curso».
2. Paso «Datos básicos»: completá «Nombre del curso», «Período académico», «Cupo», «Cuota mensual» y «Responsable». Si querés, elegí la «Modalidad».
3. En «Responsable» elegí al menos un preceptor. Es el preceptor de todo el curso.
4. Tocá «Continuar».
5. Paso «Detalle»: completá «Duración (meses)». Si querés, agregá «Horario», «Carga horaria (horas)», «Inicio de inscripción» y «Cierre de inscripción».
6. Tocá «Crear curso». Aparece «Curso creado» y se abre la ficha.
7. En la ficha, en «Temario», tocá «Agregar módulo» para cargar los módulos: «Nombre» (obligatorio), «Hs. semanales», «Orden», «Recurrencia» y «Tipo de calificación». Tocá «Agregar».

Otras acciones de la ficha del curso: «Editar», «Desactivar» o «Activar», y «Publicar en catálogo» o «Retirar del catálogo».

## Reglas

- El código de la materia tiene como máximo 4 caracteres. Una vez aprobada, el código no se puede cambiar.
- El código y el nombre no se pueden repetir en el banco.
- Una materia pendiente de aprobación no se puede editar hasta que el director la revise.
- Una materia aprobada con comisiones previas no puede cambiar la modalidad de calificación, los intentos ni las horas.
- No se pueden abrir comisiones de una materia en borrador. Tiene que estar aprobada y activa.
- El curso necesita al menos un preceptor responsable. Sin eso, el paso «Datos básicos» no deja continuar y aparece «Elegí al menos un preceptor.».
- El cupo del curso es un número entero de al menos 1. La cuota tiene que ser mayor que cero.
- El cierre de inscripción del curso no puede ser anterior a la apertura.

## Qué pasa después

Una materia aprobada y activa se puede agregar al plan de una carrera y abrir en comisiones. Un curso creado se ofrece desde «Oferta académica», donde el preceptor arma su ciclo y las comisiones.

## Errores comunes

- «Ya existe una materia con ese código o ese nombre».
- «La materia está pendiente de aprobación y no se puede editar hasta que dirección la revise».
- «Faltan datos obligatorios: cargá al menos un tipo de horas semanales y la modalidad de calificación». Pasa al enviar a aprobación con la materia incompleta.
- Intentar abrir comisiones de una materia que sigue en borrador: la pantalla de comisiones muestra que el director tiene que aprobarla antes.

**Pendiente de verificar:** quién ve la materia rechazada y dónde se muestra el motivo para Administración (el cartel «Dirección rechazó esta materia» está en el asistente de la materia). Tampoco se verificó si el Subdirector puede dar de baja o reactivar materias, ni cómo se asigna una materia al curso más allá del temario por módulos.

---
title: Abrir el ciclo lectivo
section: set-up-school-year
roles: [preceptor, administrativo, subdirector, director]
status: borrador
screens: ["periodos", "periodos/nuevo", "periodos/:termId/editar", "financiero/anios-lectivos"]
last_review: 2026-11-28
---

## Para qué sirve

Un período lectivo es el calendario de cursada y de inscripción de un año: un anual, un cuatrimestre o un intensivo de verano. Sin período no se pueden armar comisiones ni abrir la inscripción. Crearlo es el paso que da comienzo al año.

## Quién puede

Preceptor, Administrativo, Subdirector y Director. En el menú aparece como «Períodos».

La configuración de cobro del año lectivo de cada carrera es otra pantalla y la hace solo el Director (ver «Reglas»).

## Pasos

Crear un período:

1. Entrá a «Períodos» y tocá «Nuevo período».
2. Elegí el «Año» y el «Tipo»: Anual, Primer cuatrimestre, Segundo cuatrimestre o Intensivo de verano.
3. Escribí la «Descripción».
4. Cargá «Inicio de cursada» y «Fin de cursada».
5. Cargá «Inicio de inscripción» y «Fin de inscripción». Son obligatorios.
6. Tocá «Crear período». Aparece «Período creado. Queda activo.».

Cambiar un período:

1. En la lista, tocá el menú de acciones de la fila y elegí «Editar período».
2. Corregí los datos y tocá «Revisar cambios».
3. Mirá la tabla de diferencias y tocá «Confirmar cambios».

Abrir o cerrar la inscripción a mano:

1. En el menú de la fila de un período activo elegí «Abrir inscripción ahora» o «Cerrar inscripción ahora».
2. Para volver a las fechas de la ventana, elegí «Quitar apertura manual». Esa opción aparece solo si hay una apertura o cierre manual.
3. Confirmá en el cuadro que se abre.

Desactivar un período: menú de la fila, «Desactivar período», y confirmar. Para volver a usarlo, «Activar período».

## Reglas

- Antes de crear un período tienen que estar cargados los feriados de ese año. Ver «Cargar los feriados».
- Todas las fechas son obligatorias. El fin de cursada no puede ser anterior al inicio. El fin de la inscripción no puede ser anterior a su inicio.
- La inscripción no puede cerrar después de que termina el cuatrimestre.
- Solo puede haber un período activo del mismo año y tipo. Si ya hay uno, el sistema pide desactivar el anterior o elegir otro año o tipo.
- No se puede desactivar un período que tiene inscripciones activas o en espera.
- Si un período está inactivo o ya terminó, no se pueden crear ciclos ni comisiones en él.
- Un período nuevo queda activo desde que se crea.
- Año lectivo de cobro: cada carrera necesita un año lectivo configurado, que define cuántas cuotas se facturan. Si la facturación está activa y falta, no se puede crear la cohorte de esa carrera para ese año. La carga la hace el Director desde «Financiero», «Años lectivos», eligiendo la carrera y tocando «Configurar cobro».

## Qué pasa después

El período aparece en la lista con su estado, su tipo, sus fechas y su modo de inscripción: «Abierta (manual)», «Cerrada» o «Según ventana». Ya se puede elegir en el alta de ciclo de la oferta académica, en el calendario de aulas y en las cohortes.

## Errores comunes

- «No se puede crear el período: todavía no hay feriados cargados para ese año». Pedile a Administración que los cargue y volvé a intentar.
- «Ya hay un período activo de este año y tipo». Desactivá el anterior o cambiá el tipo.
- «No se pudo guardar: el cambio entra en conflicto con cursadas, comisiones o la configuración de cobro ya cargadas». Pasa al cambiar fechas de un período que ya tiene movimiento.
- Querer editar el año o el tipo de un período ya creado: esos dos campos no se cambian. Hay que crear otro.

**Pendiente de verificar:** el circuito completo de configuración de cobro del año lectivo (qué se carga en «Configurar cobro», matrícula anticipada y cuotas). Es cobranza y queda fuera de esta rebanada. Administrativo y Subdirector ven los años lectivos de cada carrera en solo lectura, en la solapa «Años lectivos» de la carrera, con el aviso «Solo el director puede modificar la configuración de cobro».

---
title: Enviar invitaciones por oleadas
section: notices-and-invitations
roles: [administrativo, subdirector, director]
status: borrador
screens: ["invitaciones", "invitaciones/nueva", "invitaciones/:waveId", "invitaciones/:waveId/vista-previa"]
last_review: 2026-11-28
---

## Para qué sirve

Para invitar por mail, de a tandas, a los alumnos que se cargaron con la importación inicial y todavía no entraron al sistema. Cada persona recibe un mail de activación y elige su propia contraseña. Nunca se envían contraseñas.

Cada tanda de invitaciones se llama «oleada» y se arma a partir de un lote de importación ya importado.

## Quién puede

Administrativo, Subdirector y Director. Se entra desde el menú Personas, en «Invitaciones».

Ver, crear y enviar son permisos distintos. Si falta alguno, la pantalla lo avisa y te indica pedírselo al Director.

## Pasos

1. En «Invitaciones» ves la lista de oleadas con su estado, el lote, la fecha de creación y el progreso. Tocá «Nueva oleada».
2. Escribí el «Nombre» de la oleada, por ejemplo «Ingresantes 2027».
3. En «Lote de importación», elegí el lote. Solo aparecen lotes ya importados. Cada opción muestra cuántas personas tiene sin activar.
4. Revisá o ajustá el «Tamaño de tanda» (mails por tanda), el «Ritmo (minutos)» entre tandas y el «Tope diario». Abajo ves un resumen de lo que se va a enviar y el horario en que salen los mails.
5. Tocá «Crear borrador». Todavía no se envía ningún mail.
6. En «Vista previa de la oleada» revisá las pestañas «Elegibles» y «Excluidos», con el motivo de cada exclusión. También ves un estimado de tandas y días.
7. Tocá «Ir a la oleada». Para empezar a enviar, tocá «Iniciar oleada». En la ventana de confirmación mirá los destinatarios pendientes, el ritmo, el tope diario y el horario de envío, y confirmá.
8. Para seguir el avance, abrí la oleada. Ves la barra de avance y contadores filtrables: «Pendientes», «En cola», «Enviados», «Fallidos», «Excluidos» y «Activaron».
9. Mientras está en curso, podés tocar «Pausar». Para seguir, tocá «Reanudar».
10. Si hubo fallos, tocá «Reintentar fallidos». Cada persona fallida muestra el motivo.
11. Para bajar el detalle, usá «Exportar reporte CSV».
12. Para frenar del todo, tocá «Cancelar oleada» y confirmá.

## Reglas

- La oleada nace como «Borrador». Recién se envía cuando alguien la inicia.
- Estados de una oleada: «Borrador», «En curso», «Pausada», «Completada» y «Cancelada».
- Iniciar, reanudar, reintentar fallidos y cancelar necesitan el permiso de enviar invitaciones. Pausar y exportar el reporte no.
- El tope diario no puede superar el máximo del instituto, y la tanda no puede superar el tope diario ni el máximo permitido de tanda.
- El ritmo tiene que estar entre 1 y 1440 minutos.
- Un lote con más personas que el máximo de una oleada no se puede elegir. El mensaje indica cuál es el máximo.
- Los mails salen solo dentro del horario de envío del instituto, que se muestra en pantalla.
- Quedan excluidas las personas sin email, las que ya activaron su cuenta, las no elegibles, las que ya están en otra oleada y las que comparten mail con un tutor.
- Cancelar no se puede deshacer. Los pendientes quedan libres para invitarlos en otra oleada. Los mails ya enviados no se retiran y sus enlaces siguen vigentes.
- «Reintentar fallidos» solo está disponible con la oleada «En curso».
- Con permiso de solo lectura se puede ver el avance y exportar el reporte, pero no cambiar nada.

## Qué pasa después

- Cada persona recibe un enlace para elegir su contraseña y activar su cuenta. Al hacerlo pasa a «Activó».
- Una oleada en curso se actualiza sola cada pocos segundos.
- Cuando se procesa a todos, la oleada queda «Completada».
- Las personas que no pudieron recibir el mail figuran como «Fallido» con el motivo (por ejemplo, el proveedor de mail no respondió).

## Errores comunes

- No aparece ningún lote al crear la oleada: solo se listan lotes ya importados.
- «No hay personas para invitar»: el lote no tiene personas sin activar. Armá otra oleada con otro lote.
- Los botones de iniciar o cancelar aparecen bloqueados: falta el permiso de enviar invitaciones. Pedíselo al Director.
- Se ven números de persona en vez de nombres: no tenés permiso para ver datos personales.

**Pendiente de verificar:**

- Los valores máximos (tope diario del instituto, máximo de tanda, máximo de personas por oleada) los define el servidor; esta guía no los fija.
- Cada cuánto exactamente se actualiza la oleada en curso: el código consulta cada 15 segundos, pero no se probó en pantalla.

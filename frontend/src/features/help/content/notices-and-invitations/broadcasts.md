---
title: Enviar una comunicación
section: notices-and-invitations
roles: [administrativo, subdirector, director]
status: borrador
screens: ["comunicaciones", "comunicaciones/nuevo", "comunicaciones/:broadcastId", "/baja"]
last_review: 2026-11-28
---

## Para qué sirve

Para mandar un mismo mensaje institucional a un grupo de personas: alumnos de una carrera o materia, o personal. Se envía por correo o por WhatsApp manual.

La pantalla «Comunicaciones» es el registro de todos los envíos y muestra cómo va cada uno.

## Quién puede

Administrativo, Subdirector y Director. Se entra desde el menú Personas, en «Comunicaciones».

## Pasos

1. En «Comunicaciones» podés filtrar la lista por estado: «Todos», «Borrador», «Confirmado», «Enviando», «Completado» o «Cancelado».
2. Para armar un envío nuevo, abrí el asistente «Nuevo envío». Tiene cuatro pasos: «Canal y categoría», «Destinatarios», «Mensaje» y «Revisión».
3. En «Canal y categoría», escribí un «Nombre interno» (solo lo ves vos) y elegí el canal: «Correo» o «WhatsApp manual». La categoría es «Institucional».
4. En «Destinatarios», elegí al menos un criterio: «Carrera», «Materia», «Estado de cursada», o «Personal» (Dirección, Administración, Preceptoría, Docencia). Si aparece la opción, también podés marcar «Con deuda vencida».
5. A la derecha ves cuántas personas «Reciben» y cuántas quedan «Excluidos», con el motivo. Tocá un motivo para ver quiénes son. Tocá «Siguiente».
6. En «Mensaje», elegí una plantilla o dejá «En blanco». Si es por correo, completá el «Asunto». Escribí el «Mensaje».
7. Para personalizar, tocá una variable: {{nombre}}, {{apellido}}, {{carrera}}, {{materia}}, {{comision}}, {{institucion}} o {{linkBaja}}.
8. Elegí en «Vista previa para» a una persona y mirá cómo le llega.
9. Si cambiaste el texto de una plantilla, podés tocar «Guardar como nueva» o «Sobrescribir plantilla». Sobrescribir cambia el texto de los envíos futuros que la usen.
10. En «Revisión» controlá los datos y tocá «Confirmar envío». Se abre una ventana que dice a cuántas personas se envía y en cuántos días. Tocá «Enviar».
11. Para seguirlo, volvé a «Comunicaciones» y abrí el envío. Ves cuántos fueron «Enviados», «Fallidos», «Excluidos» y «Pendientes», y la lista de destinatarios con su estado.
12. Para frenar un envío, abrilo y tocá «Cancelar envío». Escribí el motivo (mínimo 10 caracteres) y confirmá.

## Reglas

- Hace falta un nombre interno para avanzar del primer paso.
- Para avanzar de «Destinatarios» tiene que haber al menos una persona que reciba el mensaje.
- El cuerpo del mensaje es obligatorio y admite hasta 5000 caracteres. El asunto admite hasta 300 y es obligatorio en los correos.
- Si escribís una variable que no existe, el asistente lo marca.
- La categoría «Promocional» está deshabilitada: hasta validar el marco legal solo salen avisos institucionales.
- La ventana de confirmación estima los días de envío calculando 200 correos por día: un grupo de 600 personas figura con 3 días.
- Quedan fuera del envío las personas sin correo verificado, dadas de baja, sin consentimiento, sin teléfono válido o sin dirección para el canal.
- Si a algunas personas les falta un dato del mensaje, aparece el botón «Excluir a esos» para dejarlas afuera.
- Un envío confirmado no se puede deshacer, pero se pueden cancelar los pendientes. Solo se cancela un envío en estado «Borrador», «Confirmado» o «Enviando».
- Si salís del asistente con «Salir sin guardar», se guarda un borrador en este navegador para retomarlo. Podés descartarlo desde esa ventana.

## Qué pasa después

- Los correos empiezan a salir el mismo día y siguen en los días siguientes según la cantidad.
- Los envíos pasan por «Confirmado», «Enviando» y «Completado». Los cancelados muestran el motivo.
- Cada correo lleva un enlace para darse de baja. Al abrirlo, la persona ve la pantalla «Darte de baja», confirma con «Confirmar baja» y deja de recibir comunicaciones de ese tipo por ese canal.
- Si la persona se da de baja de los avisos institucionales, la pantalla le aclara que la cobranza y los avisos obligatorios siguen llegando. Puede volver a activarlo desde «Mi perfil».
- Confirmar y cancelar un envío queda registrado en la Auditoría.

## Errores comunes

- No se puede avanzar a «Mensaje»: todavía no elegiste criterios, o nadie del grupo puede recibir el envío.
- «No hay destinatarios elegibles»: al confirmar, nadie quedó en condiciones de recibirlo. Tocá «Elegir otro segmento».
- «Sin correo verificado» o «Se dio de baja» explican por qué una persona figura como «Excluido».
- No se puede cancelar un envío «Completado» o ya «Cancelado».

**Pendiente de verificar:**

- La lista «Comunicaciones» no tiene un botón visible para abrir «Nuevo envío»; la pantalla existe en la ruta «comunicaciones/nuevo». Falta confirmar con Gabriel desde dónde debe abrirse.
- La opción «Con deuda vencida» y los criterios de «Personal» solo aparecen si el servidor los ofrece; no está confirmado cuándo.
- Si el Subdirector entra por la misma pantalla que el Administrativo se ve en el código, pero no se probó en pantalla.

---
title: Crear el primer director
section: institution
roles: [platform_admin]
status: borrador
screens: ["primer-director", "inicio"]
last_review: 2026-11-28
---

## Para qué sirve

Para dar de alta un instituto nuevo junto con su primer Director.

## Quién puede

Solo el administrador de la plataforma. Al entrar, la pantalla de inicio lleva directo a «Primer director».

## Pasos

1. Entrá a «Primer director». Arriba dice «Se hace una sola vez».
2. En «Institución», escribí el «Nombre de la institución». Si la institución ya existe, el campo viene fijo con «Ya existe» y solo se le asigna el Director.
3. En «Director», completá «Nombre», «Apellido», «Email» y «Documento» (tipo y número).
4. En «Motivo», escribí por qué se da de alta este Director.
5. Si falta algo, la pantalla dice «Para crear falta:» con la lista de campos. Cuando está todo, dice «Listo para crear».
6. Tocá «Crear Director».
7. Revisá el resumen en «¿Crear el primer Director?» (institución, Director y email). Dice «Se envía un email y no se puede deshacer». Tocá «Sí, crear Director».
8. Al terminar ves «Director creado» con el resumen y los pasos que siguen.

## Reglas

- Se hace una sola vez. Si ya hay un Director, la pantalla muestra «Ya existe un Director» y no deja crear otro.
- El nombre de la institución, el nombre, el apellido y el email admiten hasta 255 caracteres. El documento admite hasta 50.
- El email tiene que tener un formato válido.
- El motivo necesita entre 5 y 500 caracteres. Queda en la Auditoría.
- Si el email o el documento ya pertenecen a otra persona, la pantalla lo marca en ese campo.
- Si se corta la conexión, se puede reintentar: si el Director ya se había creado, no se duplica.

## Qué pasa después

- El sistema envía un email al Director con un código para crear su contraseña. El código vale 15 minutos.
- Si el email no llega, el Director puede usar «Recuperar contraseña» desde el login.
- El alta queda registrada en la Auditoría.

## Errores comunes

- «Ya hay una persona registrada con este email» o «con este documento»: ya existe esa persona. Revisá los datos.
- «No pudimos confirmar si se creó» o «No se pudo confirmar la creación»: reintentá; no se duplica.
- «No tenés permiso para esta pantalla»: solo la plataforma puede dar de alta un Director.
- «No pudimos revisar si ya existe un Director»: revisá la conexión; no se creó nada.

**Pendiente de verificar:**

- Los tipos de documento disponibles además de DNI.

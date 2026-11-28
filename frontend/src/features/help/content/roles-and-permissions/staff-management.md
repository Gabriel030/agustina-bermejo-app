---
title: Dar de alta y gestionar al personal
section: roles-and-permissions
roles: [administrativo, subdirector, director]
status: borrador
screens: ["personal", "personal/:id"]
last_review: 2026-11-28
---

## Para qué sirve

Para dar de alta a las personas que trabajan en el instituto, cambiar sus datos de contacto, darlas de baja y reactivarlas. Se hace desde la pantalla «Gestión de personal».

## Quién puede

Administrativo, Subdirector y Director. Cada uno gestiona roles distintos:

- Administrativo y Subdirector: Docente y Preceptor.
- Director: Administrativo y Subdirector.

## Pasos

**Dar de alta**

1. Entrá a «Personal» (en el grupo «Personas» del menú).
2. Tocá «Nuevo personal». Se abre «Alta de personal».
3. Completá «Nombre», «Apellido», «Email», «Documento» (el tipo y el número) y «Rol». Son obligatorios.
4. Si querés, completá el domicilio: «Calle», «Número», «Piso», «Ciudad» y «CP».
5. Tocá «Crear personal».
6. Aparece «…quedó dado de alta. Le enviamos un email para que elija su contraseña.» La persona activa su cuenta desde ese correo (ver «Ingresar y activar la cuenta»).

**Buscar a una persona**

1. En «Gestión de personal», escribí en «Buscar por nombre, apellido o DNI…».
2. Con las solapas «Activos» y «Bajas» elegís qué lista ver.
3. Tocá una fila para abrir su legajo.

**Cambiar datos de contacto**

1. En el legajo, tocá «Editar datos». Se abre «Editar datos de contacto».
2. Cambiá el email, el teléfono o el domicilio.
3. Tocá «Guardar cambios».
4. Si cambiaste el email, hay un paso más de confirmación: ver «Cambiar el email de la cuenta».

**Resetear la contraseña**

1. En el legajo, en «Acciones», tocá «Resetear contraseña».
2. Confirmá con «Enviar link». El link de recuperación llega al email de la persona.

**Dar de baja**

1. En el legajo de una persona activa, tocá «Dar de baja».
2. Confirmá con «Confirmar baja».

**Reactivar**

1. Abrí la solapa «Bajas» y entrá al legajo.
2. Tocá «Reactivar» y confirmá con «Confirmar reactivación».

## Reglas

- Cada rol solo da de alta y gestiona los roles indicados arriba. El Director ve a todo el personal, pero en los legajos de Docentes y Preceptores no aparecen las acciones.
- Un Administrativo o un Subdirector solo ven en la lista a Docentes y Preceptores.
- El DNI y el email no se pueden repetir entre personas.
- El Nombre, el Apellido y el Documento se cargan al dar de alta. «Editar datos» permite cambiar contacto y domicilio.
- Dar de baja pasa a la persona a «Inactivo». Se puede reactivar más adelante.
- No se puede resetear la contraseña si hay un cambio de email pendiente o reciente. Hay que esperar 24 horas.
- La sección «Comisiones» del legajo todavía no está disponible.

- El primer Director no se da de alta acá: lo crea el Admin de plataforma desde «Primer director».

> Pendiente de verificar: qué pasa con las comisiones y los pendientes de una persona al darla de baja.

## Qué pasa después

- Alta: la persona recibe un correo para elegir su contraseña.
- Baja: la persona queda inactiva. Aparece en la solapa «Bajas».
- Reactivación: vuelve a estado activo y puede acceder al sistema.
- Resetear contraseña: la persona recibe un link para elegir una nueva.

## Errores comunes

- «Ya existe una persona con ese DNI o ese email.» Revisá los datos. No se puede dar de alta dos veces.
- «No tenés permiso para dar de alta personal con ese rol.» Tu rol no gestiona ese rol.
- «Revisá los datos: completá los campos obligatorios y usá un email válido.»
- «No se pudo dar de alta al personal. Intentá de nuevo.»
- «No tenés permiso para gestionar a esta persona.» Es de un rol que no gestionás.
- «No se puede resetear la contraseña: hay un cambio de email pendiente o reciente.»
- «No se pudo dar de baja. Intentá de nuevo.» o «No se pudo reactivar. Intentá de nuevo.»
- «No se encontraron miembros del personal que coincidan.» Probá con otra búsqueda o cambiá de solapa.

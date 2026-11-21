# Modos de la grilla de permisos: operación y rollback

Todo lo de `/permissions/*` es solo para el Director. El modo es un dato por rol y módulo; lo que no está cargado se asume `LEGACY`.

| Modo | Quién decide | Qué registra |
|---|---|---|
| `LEGACY` | La regla vieja (`legacyRoles`) | Nada |
| `SHADOW` | La regla vieja | Cada vez que la grilla habría decidido distinto, con su dirección |
| `ENFORCE` (en pantalla "Activo") | La grilla | Nada |

## Volver un módulo a LEGACY sin deploy

`PUT /permissions/modes` con un motivo obligatorio (hasta 500 caracteres) y el cambio por rol y módulo:

```json
{"reason": "Se rompió el acceso de Docentes a avisos", "changes": [{"role": "DOCENTE", "module": "announcement", "mode": "LEGACY"}]}
```

Aplica al instante (se invalida la caché de la grilla al confirmar) y deja una entrada de auditoría `PERMISSION_GRID_CHANGED` con el modo anterior, el nuevo y el motivo. Un cambio que no modifica nada no audita. El rol `DIRECTOR` no se edita.

## Leer las discrepancias

- `GET /permissions/discrepancies/summary`: por módulo, cantidad por dirección, total y última vez.
- `GET /permissions/discrepancies?module=<módulo>`: detalle por rol y permiso con `direction` y `count`. Un módulo inexistente responde 422 `UNKNOWN_PERMISSION_MODULE`.

Direcciones:

- `LEGACY_ALLOWED_GRID_DENIED`: la regla vieja dejó pasar y la grilla habría negado. Pasar a `ENFORCE` le quitaría el acceso a alguien que hoy lo tiene.
- `LEGACY_DENIED_GRID_ALLOWED`: la regla vieja negó y la grilla habría permitido. Pasar a `ENFORCE` le daría acceso a alguien que hoy no lo tiene.

Los contadores se vuelcan a la base cada 30 segundos como máximo; las lecturas fuerzan el volcado antes de responder.

## Criterio para salir de SHADOW

Pasar un módulo a `ENFORCE` solo con cero discrepancias sin explicar durante al menos un ciclo de uso completo del módulo. Una discrepancia está explicada si corresponde a un cambio de grilla deliberado, registrado en la auditoría; el resto se corrige en la grilla (o en la regla vieja) y el conteo se vigila de nuevo.

## Limpieza final de legacyRoles

Quitar `legacyRoles` del código es una limpieza de código, no un cambio de configuración: después de ese deploy ya no existe regla vieja a la que volver y `PUT /permissions/modes` en `LEGACY` deja de tener efecto. Solo se hace con todos los módulos migrados en `ENFORCE` y estables. Revertirla exige un deploy.

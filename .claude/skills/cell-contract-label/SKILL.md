---
name: cell-contract-label
description: Decidir si un PR con diff de contrato lleva la etiqueta contract-break-approved y leer la salida de scripts/contract-diff.sh (solo coordinador). Usar cuando oasdiff frena un PR, o "contract-break-approved", "el CI de contrato falla", "es rompedor este cambio".
---

# cell-contract-label (solo coordinador)

Entrada: un PR cuyo `contracts/openapi.json` cambia y el job `backend-contract` (`scripts/oasdiff-breaking.sh`) lo frena. Salida: la decisión de si el cambio es aditivo para quien consume y, solo con autorización de Gabriel, la etiqueta `contract-break-approved`.

## Cuándo se usa la etiqueta
- Solo con diff aditivo para los consumidores reales (el front y los mocks MSW). `oasdiff` es más estricto que eso y marca como ERR cosas que acá no rompen a nadie.
- Un filtro de valor único que pasa a lista (`status=A` a `status=A,B`) es aditivo: los pedidos viejos siguen siendo válidos.
- Cualquier cambio que haga fallar un pedido que hoy funciona NO es candidato: va como ruptura declarada en el plan, no con la etiqueta.

## Cómo correr el script
```
scripts/contract-diff.sh origin/develop HEAD
```
Compara `contracts/openapi.json` entre dos refs de git (`git show <ref>:contracts/openapi.json`), con solo bash y node. Los refs deben estar traídos (`git fetch` antes). Informa, no bloquea: sale siempre con código 0.

## Cómo leer la salida
Una línea por hallazgo, `[REQUEST]`, `[RESPONSE]` o `[GENERAL]` (paths y operaciones removidos, esquemas sin sufijo), y un resumen final con el conteo. La clasificación sale del nombre del esquema (`Request`/`Command`/`Input` es pedido, `Response` es respuesta) o del lugar donde aparece (request body, parámetro, respuesta).

| Hallazgo | Veredicto |
|---|---|
| `[RESPONSE]` nuevo required | No rompe: el servidor siempre lo manda y quien consume ignora campos que no usa |
| `[RESPONSE]` pasa a nullable (string a nullable) | No rompe si el front ya tolera `null` en ese campo (revisar el uso) |
| `[RESPONSE]` valor de enum removido | No rompe: el servidor deja de mandarlo; revisar que el front no dependa de ese valor |
| `[RESPONSE]` propiedad removida o cambio de tipo | Rompe: revisar quién la lee |
| `[REQUEST]` nuevo required, parámetro o request body nuevo requerido | Rompe siempre: los pedidos viejos fallan |
| `[REQUEST]` valor de enum removido, propiedad removida, maxLength que baja | Rompe o puede romper: revisar |
| `[GENERAL]` path u operación removida | Rompe |

Un diff con solo hallazgos `[RESPONSE]` de los tres primeros tipos es candidato a la etiqueta. Un solo `[REQUEST]` de los que rompen la descarta.

## Reglas
- La etiqueta la pone el coordinador, solo con autorización de Gabriel. Ni el líder ni los workers la agregan por su cuenta.
- Al pedir la autorización, pegar la salida del script en el mensaje y en el PR.
- El workflow solo relee las etiquetas en `opened`, `synchronize`, `reopened` y `ready_for_review`: poner la etiqueta con el PR ya abierto no re-dispara el job. Hay que cerrar y reabrir el PR.

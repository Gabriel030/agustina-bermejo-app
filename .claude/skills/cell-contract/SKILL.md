---
name: cell-contract
description: Del mockup aprobado (o del ticket, si es solo backend) al PR de contrato (solo líder técnico), ANTES de lanzar workers. Deriva campos, errores, enums, filtros y orden; actualiza contracts/openapi.json, mocks MSW y mensajes de error. Usar cuando el diseño esté aprobado o el ticket sea tipo A, o "cell-contract", "armá el contrato".
---

# cell-contract (solo líder técnico)

Entrada: mockup aprobado (tipos B/C) o el ticket (tipo A). Salida: un PR chico de contrato, mergeado ANTES de lanzar los workers.

## Pasos
1. Derivar de cada pantalla y estado del mockup: campos de la respuesta (con moneda y unidades), request y validaciones, códigos de error de dominio, enums de estado (el front los traduce a castellano), filtros, orden y paginación (las listas devuelven `content`), permisos por rol.
2. Backend: controllers y DTO mínimos del contrato (firmas, sin lógica; la implementación es del worker). Regenerar el snapshot: `cd backend && ./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true` (`OpenApiSnapshotTest` escribe `contracts/openapi.json` con esa propiedad) y revisar el diff: solo aditivo salvo ruptura declarada en el plan. Una sola invocación de Maven, con semáforo.
3. Frontend: mocks MSW en `frontend/src/test/mocks/handlers.js` con todos los campos y los estados del mockup (vacío, error, sin permiso); tipos del módulo; un mensaje por cada código de error nuevo en `frontend/src/api/domainErrorMessages.js`. Los mocks se validan contra el contrato (`frontend/src/test/contract/`).
4. Migraciones y eventos: número `V*.sql` reservado y eventos/puertos `<modulo>.api` en la sección Contratos del plan.
5. PR chico contra `develop`, `Refs #<issue>`, con el diff de `openapi.json` en el cuerpo; se mergea primero. Cada worker parte de `origin/develop` ya con el contrato.

## Reglas
- Backend implementa el contrato tal cual; el frontend trabaja contra los mocks.
- Cambio de contrato a mitad de camino: el worker manda `NECESITO DECISIÓN` (no lo modifica); el líder hace un PR de ajuste de contrato, lo mergea y avisa al otro worker.
- `oasdiff` en el CI (`scripts/oasdiff-breaking.sh`) frena cambios incompatibles sin la etiqueta `contract-break-approved`; solo con diff enum-value-added o ruptura declarada, listando el diff en el PR (cerrar y reabrir).

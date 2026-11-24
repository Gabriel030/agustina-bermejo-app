# #1075 — nombres en CertificateExceptionResponse (backend-4)

## Criterios
- [x] studentName, programName, requestedByName, decidedByName nullable en la respuesta: pasa (unit + IT)
- [x] Sin N+1 en el listado: pasa (IT compara cantidad de statements con 1 vs 4 filas; sabotaje hecho: con `map(responses::of)` por fila rompe)
- [x] AuthorizationMatrixTest, ModularityTest, architecture/*: pasan
- [ ] contracts/openapi.json: BLOQUEADO, OpenApiSnapshotTest rojo; lo regenera líder-frontend
- [ ] Mocks MSW: fixture actualizada, sin correr vitest (sin node_modules en la sesión) ni validar contra el snapshot nuevo
- [ ] Asistencia: NECESITO DECISIÓN enviada al líder; el front no cruza asistencia hoy

## Notas
- Hecho: CertificateExceptionResponses pasa a componente con PersonNameFinder y EnrollmentNameFinder; Reader, Decider y Requester lo inyectan.
- Sin exposición nueva: los roles del endpoint (ADMINISTRATIVO, DIRECTOR) ya ven nombres de alumno y carrera en /regular-certificate-requests.

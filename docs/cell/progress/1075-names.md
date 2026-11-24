# #1075 — nombres en CertificateExceptionResponse (backend-4)

## Criterios
- [x] studentName, programName, requestedByName, decidedByName nullable en la respuesta: pasa (unit + IT)
- [x] Sin N+1 en el listado: pasa (IT compara statements con 1 vs 4 filas; con sabotaje)
- [x] AuthorizationMatrixTest, ModularityTest, architecture/**: pasan
- [x] contracts/openapi.json y mocks MSW: solo adiciones
- Asistencia: fuera de este PR (decisión del líder)

## Notas
- CertificateExceptionResponses pasa a componente con PersonNameFinder y EnrollmentNameFinder; Reader, Decider y Requester lo inyectan.
- Sin exposición nueva: los roles del endpoint ya ven estos nombres en /regular-certificate-requests.

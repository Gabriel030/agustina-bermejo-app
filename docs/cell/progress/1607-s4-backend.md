# E28-F S4 backend: señales por fila del padrón

## Criterios
- [x] 1 Señales en el DTO sin consultas por fila (EducationListQueryCountTest: tope de 2 a 6, ver PR)
- [x] 2 Preceptor y Subdirector no reciben el motivo de baja (StudentControllerTest por rol)
- [x] 3 80 % consumido o libre muestra la señal (StudentAttendanceRiskPolicyTest y EducationListQueryCountTest)
- [x] Contrato aditivo: attendanceRisk, missingRequiredDocuments + openapi + fixture MSW

## Notas
- Hecho: contrato, puertos de identity (riesgo y documentos), adaptadores en attendance y enrollment, política de riesgo, corrección del motivo de baja para Subdirector.
- Sin migración.

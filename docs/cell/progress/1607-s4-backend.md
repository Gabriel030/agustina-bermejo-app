# E28-F S4 backend: señales por fila del padrón

## Criterios
- [ ] 1 Señales en el DTO sin consultas por fila (EducationListQueryCountTest con tope justificado)
- [ ] 2 Preceptor y Subdirector no reciben el motivo de baja (test por rol)
- [ ] 3 80 % consumido o libre muestra la señal (test de borde)
- [x] Contrato aditivo: attendanceRisk, missingRequiredDocuments + openapi + fixture MSW

## Notas
- Hecho: PR de contrato (DTO con valores por defecto NONE / vacío).
- Sigue: lectura por lote de riesgo de asistencia y documentos faltantes.

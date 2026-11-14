export const IMPORT_BLOCKS = [
  {
    entityType: 'PERSONS',
    label: 'Personas',
    description: 'Alumnos y docentes. El mail es obligatorio y no puede repetirse.',
    templateName: 'plantilla-personas.csv',
    templateColumns: [
      'documentType', 'dni', 'firstName', 'lastName', 'role', 'sex', 'birthDate', 'email', 'phone', 'nationality',
      'street', 'city', 'province', 'postalCode', 'legajo', 'registeredAt', 'status', 'withdrawalDate',
      'guardianFirstName', 'guardianLastName', 'guardianDocumentType', 'guardianDni', 'guardianRelationship',
      'guardianPhone', 'guardianEmail', 'emergencyContactFirstName', 'emergencyContactLastName',
      'emergencyContactRelationship', 'emergencyContactPhone',
    ],
  },
  { key: 'academic-structure', label: 'Estructura académica', description: 'Carreras con precio, materias, plan de estudios y correlativas.', upcoming: true },
  { key: 'historical-sections', label: 'Comisiones históricas', description: 'Años y comisiones ya cerradas, con docente y datos del acta.', upcoming: true },
  { key: 'historical-enrollments', label: 'Inscripciones y cursadas', description: 'Quién cursó qué, con la condición final, la nota y la fecha.', upcoming: true },
  { key: 'graduations', label: 'Egresos', description: 'Quién egresó y cuándo. No se generan certificados.', upcoming: true },
];

export function blockLabel(entityType) {
  return IMPORT_BLOCKS.find((block) => block.entityType === entityType)?.label ?? entityType;
}

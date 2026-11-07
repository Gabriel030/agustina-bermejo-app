export function profileFixture(overrides = {}) {
  return {
    id: 1,
    nombre: 'Ana',
    apellido: 'Paz',
    email: 'ana@demo.test',
    dni: '30111222',
    documentType: 'DNI',
    legajo: 'L-1',
    sexo: 'F',
    telefono: '3511234567',
    street: 'Calle',
    number: '1',
    floor: '',
    city: 'Córdoba',
    state: 'Córdoba',
    country: 'Argentina',
    postalCode: '5000',
    hasAvatar: false,
    roles: ['ALUMNO'],
    ...overrides,
  };
}

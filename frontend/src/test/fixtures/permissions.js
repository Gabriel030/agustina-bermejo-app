export const permissionCatalog = () => ({
  modules: [
    {
      module: 'announcement',
      permissions: [
        { code: 'announcement.create', description: 'Crear avisos', fixedToDirector: false },
        { code: 'announcement.delete', description: 'Eliminar avisos', fixedToDirector: false },
      ],
    },
    {
      module: 'billing',
      permissions: [{ code: 'billing.payment.revoke', description: 'Revocar la aprobación de un pago', fixedToDirector: true }],
    },
  ],
});

export const permissionGrid = (overrides = {}) => ({
  roles: [
    { role: 'DIRECTOR', permissionCodes: ['announcement.create', 'announcement.delete', 'billing.payment.revoke'] },
    { role: 'ADMINISTRATIVO', permissionCodes: ['announcement.create'] },
    { role: 'PRECEPTOR', permissionCodes: [] },
    { role: 'DOCENTE', permissionCodes: [] },
  ],
  modes: [
    { role: 'ADMINISTRATIVO', module: 'announcement', mode: 'SHADOW' },
    { role: 'DOCENTE', module: 'announcement', mode: 'LEGACY' },
  ],
  editable: true,
  ...overrides,
});

export const permissionDiscrepancy = (overrides = {}) => ({
  role: 'ADMINISTRATIVO',
  module: 'announcement',
  permissionCode: 'announcement.delete',
  count: 3,
  lastSeenAt: '2026-11-21T12:00:00Z',
  ...overrides,
});

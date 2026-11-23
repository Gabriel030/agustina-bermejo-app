export const permissionCatalog = () => ({
  modules: [
    {
      module: 'announcement',
      permissions: [
        { code: 'announcement.create', description: 'Crear avisos', fixedToDirector: false, sensitive: false },
        { code: 'announcement.delete', description: 'Eliminar avisos', fixedToDirector: false, sensitive: false },
      ],
    },
    {
      module: 'billing',
      permissions: [{ code: 'billing.payment.revoke', description: 'Revocar la aprobación de un pago', fixedToDirector: false, sensitive: true }],
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

export const permissionGridReadOnly = () =>
  permissionGrid({
    editable: false,
    roles: [
      { role: 'DIRECTOR', permissionCodes: ['announcement.create', 'announcement.delete', 'billing.payment.revoke'] },
      { role: 'SUBDIRECTOR', permissionCodes: ['announcement.create'] },
      { role: 'ADMINISTRATIVO', permissionCodes: ['announcement.create'] },
      { role: 'PRECEPTOR', permissionCodes: [] },
      { role: 'DOCENTE', permissionCodes: [] },
    ],
  });

export const permissionDiscrepancy = (overrides = {}) => ({
  role: 'ADMINISTRATIVO',
  module: 'announcement',
  permissionCode: 'announcement.delete',
  direction: 'LEGACY_DENIED_GRID_ALLOWED',
  count: 3,
  lastSeenAt: '2026-11-21T12:00:00Z',
  ...overrides,
});

export const permissionDiscrepancySummary = () => ({
  modules: [
    { module: 'announcement', legacyAllowedGridDenied: 1, legacyDeniedGridAllowed: 2, total: 3, lastSeenAt: '2026-11-21T12:00:00Z' },
  ],
});

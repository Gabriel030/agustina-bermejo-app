import { lazyPage } from '@/routes/lazyPage';
const AuditLogPage = lazyPage(() => import('@/features/audit/pages/AuditLogPage'), 'AuditLogPage');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['director'], path: 'auditoria', element: <AuditLogPage endpoint="/audit-log" deniedAudience="Dirección" /> },
    { roles: ['platform_admin'], path: 'auditoria', element: <AuditLogPage endpoint="/platform/audit-log" deniedAudience="el dueño de la plataforma" /> },
  ],
};

import { RoleShell } from '@/layouts/RoleShell';

// Sidebar reducido para /catalogo y /mis-solicitudes* — lo ve cualquier persona
// con sesión (tenga rol o no todavía), no solo alguien recién registrado sin
// rol (decisión revisada de la Fase 2: ver Spec técnica).
const ITEMS = [
  { to: '/catalogo', label: 'Catálogo' },
  { to: '/mis-solicitudes', label: 'Mis solicitudes' },
];

export function AutoservicioLayout() {
  return <RoleShell titulo="Autoservicio" items={ITEMS} />;
}

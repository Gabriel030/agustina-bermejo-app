import { RoleShell } from '@/layouts/RoleShell';

// Sidebar reducido para /catalogo y /mis-solicitudes* — lo ve cualquier persona
// con sesión (tenga rol o no todavía), no solo alguien recién registrado sin
// rol (decisión revisada de la Fase 2: ver Spec técnica). "Mis solicitudes"
// pasó a ser una tab de Catálogo (Épica 7 Fase 3), ya no un ítem aparte.
const ITEMS = [{ to: '/catalogo', label: 'Catálogo' }];

export function AutoservicioLayout() {
  return <RoleShell titulo="Autoservicio" items={ITEMS} />;
}

import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/administrativo/inicio', label: 'Inicio' },
  { to: '/administrativo/alumnos', label: 'Alumnos' },
  { to: '/administrativo/facturacion', label: 'Facturación' },
  { to: '/administrativo/constancias', label: 'Constancias' },
];

export function AdministrativoLayout() {
  return <RoleShell titulo="Administrativo" items={ITEMS} />;
}

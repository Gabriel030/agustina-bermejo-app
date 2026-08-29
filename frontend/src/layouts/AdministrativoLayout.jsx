import { Home, Users, Receipt, FileText } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/administrativo/inicio', label: 'Inicio', icon: Home },
  { to: '/administrativo/alumnos', label: 'Alumnos', icon: Users },
  { to: '/administrativo/facturacion', label: 'Facturación', icon: Receipt },
  { to: '/administrativo/constancias', label: 'Constancias', icon: FileText },
];

export function AdministrativoLayout() {
  return <RoleShell titulo="Administrativo" items={ITEMS} />;
}

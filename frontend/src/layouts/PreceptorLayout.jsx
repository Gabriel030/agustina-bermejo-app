import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/preceptor/inicio', label: 'Inicio' },
  { to: '/preceptor/alumnos', label: 'Alumnos' },
  { to: '/preceptor/oferta-academica', label: 'Oferta académica' },
  { to: '/preceptor/mesas-de-examen', label: 'Mesas de examen' },
  { to: '/preceptor/periodos', label: 'Períodos' },
];

export function PreceptorLayout() {
  return <RoleShell titulo="Preceptor" items={ITEMS} />;
}
